package com.pohforest;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "POH Forest",
	description = "Surrounds your player-owned house with a client-side forest",
	tags = {"poh", "house", "forest", "trees"}
)
public class PohForestPlugin extends Plugin
{
	// Same POH regions RuneLite core uses (DiscordGameEventType.REGION_POH)
	private static final Set<Integer> POH_REGIONS = Set.of(7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);

	/** Scale is bucketed to this many percent so equal-looking trees share one Model. */
	private static final int SCALE_STEP = 8;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PohForestConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PohForestOverlay overlay;

	@Getter(AccessLevel.PACKAGE)
	private boolean inPoh;
	@Getter(AccessLevel.PACKAGE)
	private int treeCount;
	@Getter(AccessLevel.PACKAGE)
	private int undergrowthCount;
	@Getter(AccessLevel.PACKAGE)
	private long spawnMillis;
	@Getter(AccessLevel.PACKAGE)
	private String houseSummary = "-";
	@Getter(AccessLevel.PACKAGE)
	private String densityCurve = "-";
	@Getter(AccessLevel.PACKAGE)
	private ForestPreset activePreset = ForestPreset.MIXED_DECIDUOUS;

	private final List<RuneLiteObject> objects = new ArrayList<>();
	private final Map<String, Model> models = new HashMap<>();
	private boolean spawned;

	private final HotkeyListener cycleHotkey = new HotkeyListener(() -> config.cycleKey())
	{
		@Override
		public void hotkeyPressed()
		{
			ForestPreset[] all = ForestPreset.values();
			ForestPreset next = all[(config.preset().ordinal() + 1) % all.length];
			configManager.setConfiguration(PohForestConfig.GROUP, "preset", next);
		}
	};

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(cycleHotkey);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(cycleHotkey);
		overlayManager.remove(overlay);
		despawn();
		models.clear();
		inPoh = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// The scene is rebuilt on LOADING; objects from the old scene must go
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			despawn();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PohForestConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// Respawn straight away so a setting can be judged without leaving the house
		clientThread.invokeLater(() ->
		{
			despawn();
			models.clear();
			if (inPoh && client.getGameState() == GameState.LOGGED_IN)
			{
				spawn();
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean nowInPoh = isInPohNow();
		if (nowInPoh != inPoh)
		{
			inPoh = nowInPoh;
			log.info("POH {}", inPoh ? "entered" : "left");
		}

		if (!inPoh)
		{
			despawn();
		}
		else if (!spawned)
		{
			spawn();
		}
	}

	private boolean isInPohNow()
	{
		Player player = client.getLocalPlayer();
		if (player == null || !client.isInInstancedRegion())
		{
			return false;
		}
		WorldPoint wp = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return wp != null && POH_REGIONS.contains(wp.getRegionID());
	}

	private ForestStyle style()
	{
		ForestPreset preset = config.preset();
		if (preset != ForestPreset.CUSTOM)
		{
			return preset.getStyle();
		}
		ForestPreset palette = config.customPalette();
		if (palette == ForestPreset.CUSTOM)
		{
			palette = ForestPreset.MIXED_DECIDUOUS;
		}
		return palette.getStyle().withShape(
			config.customNearSpacing() / 10.0,
			Math.max(config.customNearSpacing(), config.customFarSpacing()) / 10.0,
			config.customScaleMin(),
			Math.max(config.customScaleMin(), config.customScaleMax()),
			config.customOuterBoost(),
			palette.getStyle().undergrowthSpacing);
	}

	private void spawn()
	{
		long start = System.nanoTime();
		activePreset = config.preset();
		ForestStyle style = style();
		Scene scene = client.getTopLevelWorldView().getScene();

		List<String> grid = new ArrayList<>();
		boolean[][] built = ForestLayout.builtTiles(scene, grid);
		ForestLayout.Result layout = ForestLayout.compute(built, style, config.clearingMargin(), config.density(),
			config.maxObjects(), config.undergrowth(), config.seed());

		for (ForestLayout.Spot spot : layout.spots)
		{
			Model model = modelFor(spot, style);
			if (model == null)
			{
				continue;
			}
			RuneLiteObject object = client.createRuneLiteObject();
			object.setModel(model);
			// Plane 0 always: lower planes stay visible from upstairs and sit on the ground
			object.setLocation(LocalPoint.fromScene(spot.tileX, spot.tileY, scene).plus(spot.offX, spot.offY), 0);
			object.setOrientation(spot.yaw);
			object.setActive(true);
			objects.add(object);
		}

		treeCount = layout.trees;
		undergrowthCount = layout.undergrowth;
		spawned = true;
		spawnMillis = (System.nanoTime() - start) / 1_000_000;
		describe(layout, style);

		log.debug("{} spawned {} trees + {} undergrowth in {} ms ({} models, {} candidates)", activePreset,
			treeCount, undergrowthCount, spawnMillis, models.size(), layout.candidates);
		log.debug("House chunks (# built, . grass filler, blank none), north up:");
		for (String row : grid)
		{
			log.debug("|{}|", row);
		}
	}

	private void describe(ForestLayout.Result layout, ForestStyle style)
	{
		int margin = config.clearingMargin();
		if (layout.houseBounds == null)
		{
			houseSummary = "no built rooms found";
		}
		else
		{
			int[] b = layout.houseBounds;
			int last = Constants.SCENE_SIZE - 1;
			// Tiles between the outermost room and the scene edge, i.e. how far a forest can reach
			houseSummary = "x " + b[0] + "-" + b[1] + ", y " + b[2] + "-" + b[3] + " | to edge W" + b[0]
				+ " E" + (last - b[1]) + " S" + b[2] + " N" + (last - b[3]);
		}
		double density = Math.max(0.1, config.density() / 100.0);
		densityCurve = String.format("%.1f -> %.1f tiles over %d (x%.1f size)",
			style.nearSpacing / density, style.farSpacing / density, style.falloffTiles,
			style.outerBoostPct / 100.0);
		log.debug("margin {} | {}", margin, houseSummary);
	}

	private Model modelFor(ForestLayout.Spot spot, ForestStyle style)
	{
		Recolour recolour = spot.undergrowth ? null : style.recolour;
		int variant = recolour == null ? 0 : spot.variant % recolour.targetHues.length;
		int scale = Math.max(SCALE_STEP, Math.round(spot.scalePct / (float) SCALE_STEP) * SCALE_STEP);
		String key = spot.species.name + "|" + scale + "|" + variant;

		Model cached = models.get(key);
		if (cached != null)
		{
			return cached;
		}

		ModelData[] parts = new ModelData[spot.species.models.length];
		for (int i = 0; i < parts.length; i++)
		{
			ModelData part = client.loadModelData(spot.species.models[i]);
			if (part == null)
			{
				log.debug("Model {} of {} did not load", spot.species.models[i], spot.species.name);
				return null;
			}
			parts[i] = part;
		}

		ModelData data = parts.length == 1 ? parts[0] : client.mergeModels(parts);
		// Own copies of the arrays we are about to change, in case the loaded data is shared
		data = data.shallowCopy().cloneVertices().cloneColors();

		for (int i = 0; i < spot.species.find.length; i++)
		{
			data.recolor(spot.species.find[i], spot.species.replace[i]);
		}
		if (recolour != null)
		{
			for (short color : distinct(data.getFaceColors()))
			{
				short mapped = recolour.map(color, variant);
				if (mapped != color)
				{
					data.recolor(color, mapped);
				}
			}
		}

		int s = 128 * scale / 100;
		Model model = data.scale(s, s, s).light();
		models.put(key, model);
		return model;
	}

	private static Set<Short> distinct(short[] colors)
	{
		Set<Short> set = new HashSet<>();
		for (short c : colors)
		{
			set.add(c);
		}
		return set;
	}

	private void despawn()
	{
		for (RuneLiteObject object : objects)
		{
			object.setActive(false);
		}
		objects.clear();
		spawned = false;
		treeCount = 0;
		undergrowthCount = 0;
	}

	@Provides
	PohForestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohForestConfig.class);
	}
}
