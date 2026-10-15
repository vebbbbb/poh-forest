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
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
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

	/** Flat 384x384 quad (two faces, colour 0); also the base under magic trees. */
	private static final int GROUND_QUAD_MODEL = 7768;
	private static final int GROUND_AMBIENT = 128;
	/** hue 15, sat 4, lightness 47: eyeballed from a screenshot of the plot grass. */
	private static final int FALLBACK_GRASS = (15 << 10) | (4 << 7) | 47;

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
	private int groundCount;
	@Getter(AccessLevel.PACKAGE)
	private String groundColor = "-";
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
		boolean[][] plot = ForestLayout.plotTiles(scene);
		ForestLayout.Result layout = ForestLayout.compute(plot, style, config.clearingMargin(), config.density(),
			config.maxObjects(), config.undergrowth(), config.seed());

		// Tiles outside the plot have no height data (0); lift objects there to the plot's ground level
		int groundY = referenceHeight(built, scene);

		if (config.fakeGround())
		{
			spawnGround(scene, groundY, plot, built);
		}

		for (ForestLayout.Spot spot : layout.spots)
		{
			LocalPoint at = LocalPoint.fromScene(spot.tileX, spot.tileY, scene).plus(spot.offX, spot.offY);
			int lift = groundY - Perspective.getTileHeight(client, at, 0);
			Model model = modelFor(spot, style, lift);
			if (model == null)
			{
				continue;
			}
			RuneLiteObject object = client.createRuneLiteObject();
			object.setModel(model);
			// Plane 0 always: lower planes stay visible from upstairs and sit on the ground
			object.setLocation(at, 0);
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
			houseSummary = "no plot found";
		}
		else
		{
			int[] b = layout.houseBounds;
			int last = Constants.SCENE_SIZE - 1;
			// Tiles between the outermost room and the scene edge, i.e. how far a forest can reach
			houseSummary = "plot x " + b[0] + "-" + b[1] + ", y " + b[2] + "-" + b[3] + " | to edge W" + b[0]
				+ " E" + (last - b[1]) + " S" + b[2] + " N" + (last - b[3]);
		}
		double density = Math.max(0.1, config.density() / 100.0);
		densityCurve = String.format("%.1f -> %.1f tiles over %d (x%.1f size)",
			style.nearSpacing / density, style.farSpacing / density, style.falloffTiles,
			style.outerBoostPct / 100.0);
		log.debug("margin {} | {}", margin, houseSummary);
	}

	/** Height (raw tile-height units) of the plot's ground, taken from the first built tile. */
	private int referenceHeight(boolean[][] built, Scene scene)
	{
		for (int x = 0; x < built.length; x++)
		{
			for (int y = 0; y < built[x].length; y++)
			{
				if (built[x][y])
				{
					return Perspective.getTileHeight(client, LocalPoint.fromScene(x, y, scene), 0);
				}
			}
		}
		return 0;
	}

	/**
	 * One flat grass-coloured quad per empty chunk. Model 7768 is a 384x384 flat quad (two faces,
	 * colour 0) that magic trees use as a base; scaled up it covers a whole 8x8 chunk.
	 */
	private void spawnGround(Scene scene, int groundY, boolean[][] plot, boolean[][] built)
	{
		int[][] chunks = scene.getInstanceTemplateChunks()[0];
		int color = groundColor(scene, plot, built);
		short shaded = adjustLightness(color, config.groundLightness());
		groundColor = String.format("hsl %d/%d/%d", (shaded >> 10) & 63, (shaded >> 7) & 7, shaded & 127);

		ModelData quad = client.loadModelData(GROUND_QUAD_MODEL);
		if (quad == null)
		{
			log.debug("Ground quad model {} did not load", GROUND_QUAD_MODEL);
			return;
		}
		// Negative x scale mirrors the quad so its faces point up and are lit from above.
		// 342/128 * 384 = 1026 units: a chunk is 1024, so neighbours overlap by a hair, no gaps.
		short tint = shaded;

		for (int cx = 0; cx < chunks.length; cx++)
		{
			for (int cy = 0; cy < chunks[cx].length; cy++)
			{
				if (chunks[cx][cy] != -1)
				{
					continue;
				}
				// Centre of the chunk is the corner shared by its four middle tiles
				LocalPoint at = LocalPoint.fromScene(cx * Constants.CHUNK_SIZE + 3, cy * Constants.CHUNK_SIZE + 3, scene)
					.plus(Perspective.LOCAL_HALF_TILE_SIZE, Perspective.LOCAL_HALF_TILE_SIZE);
				RuneLiteObject object = client.createRuneLiteObject();
				object.setModel(groundModel(tint, groundY - Perspective.getTileHeight(client, at, 0)));
				object.setLocation(at, 0);
				object.setActive(true);
				objects.add(object);
				groundCount++;
			}
		}
	}

	private Model groundModel(short color, int lift)
	{
		// Heights outside the plot are all the same, so this is one model in practice; a hair
		// below the plot's own ground so the real tiles win where they meet
		String key = "ground|" + color + "|" + lift;
		return models.computeIfAbsent(key, k ->
		{
			ModelData data = client.loadModelData(GROUND_QUAD_MODEL).shallowCopy().cloneVertices().cloneColors();
			data.recolor((short) 0, color);
			// High ambient so the flat quad shows close to its own colour instead of the
			// default object shading, which is much darker than the lit terrain
			return data.scale(-342, 128, 342).translate(0, lift + 2, 0)
				.light(GROUND_AMBIENT, ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y,
					ModelData.DEFAULT_Z);
		});
	}

	/** Colour of the real grass: the first lit tile paint found on a filler chunk, else a fallback. */
	private int groundColor(Scene scene, boolean[][] plot, boolean[][] built)
	{
		Tile[][] plane = scene.getTiles()[0];
		for (int x = 0; x < plane.length; x++)
		{
			for (int y = 0; y < plane[x].length; y++)
			{
				// Open grass: inside the plot but not in a built room
				Tile tile = plane[x][y];
				if (!plot[x][y] || built[x][y] || tile == null || tile.getSceneTilePaint() == null)
				{
					continue;
				}
				SceneTilePaint paint = tile.getSceneTilePaint();
				int texture = paint.getTexture();
				int rgb = texture >= 0 ? client.getTextureProvider().getDefaultColor(texture) : paint.getRBG();
				log.debug("Grass tile {},{}: texture {} rgb {} sw {}", x, y, texture, Integer.toHexString(rgb),
					paint.getSwColor());
				if ((rgb & 0xFFFFFF) != 0)
				{
					return JagexColor.rgbToHSL(rgb & 0xFFFFFF, 1.0);
				}
			}
		}
		return FALLBACK_GRASS;
	}

	private static short adjustLightness(int color, int delta)
	{
		int lightness = Math.max(0, Math.min(127, (color & 127) + delta));
		return (short) ((color & ~127) | lightness);
	}

	private Model modelFor(ForestLayout.Spot spot, ForestStyle style, int lift)
	{
		Recolour recolour = spot.undergrowth ? null : style.recolour;
		int variant = recolour == null ? 0 : spot.variant % recolour.targetHues.length;
		int scale = Math.max(SCALE_STEP, Math.round(spot.scalePct / (float) SCALE_STEP) * SCALE_STEP);
		String key = spot.species.name + "|" + scale + "|" + variant + "|" + lift;

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
		Model model = data.scale(s, s, s).translate(0, lift, 0).light();
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
		groundCount = 0;
	}

	@Provides
	PohForestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohForestConfig.class);
	}
}
