package com.pohforest;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
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
	private static final int GROUND_AMBIENT = ModelData.DEFAULT_AMBIENT;
	/** Fallback fog colour when the client reports no skybox colour. */
	private static final int DEFAULT_SKY = 0xB4D2FF;
	/** hue 15, sat 4, lightness 47: eyeballed from a screenshot of the plot grass. */
	private static final short FALLBACK_GRASS = JagexColor.packHSL(15, 4, 47);

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
	private int coverCount;
	@Getter(AccessLevel.PACKAGE)
	private String groundColor = "-";
	@Getter(AccessLevel.PACKAGE)
	private long spawnMillis;
	@Getter(AccessLevel.PACKAGE)
	private String houseSummary = "-";
	@Getter(AccessLevel.PACKAGE)
	private String densityCurve = "-";
	@Getter(AccessLevel.PACKAGE)
	private ForestPreset activePreset = ForestPreset.DENSE_PINE;

	private final List<RuneLiteObject> objects = new ArrayList<>();
	private final Map<String, Model> models = new HashMap<>();
	private boolean spawned;
	private int fogRgb = DEFAULT_SKY;
	private int hazeSpan = 1;

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
			palette = ForestPreset.DENSE_PINE;
		}
		return palette.getStyle().withShape(
			config.customNearSpacing() / 10.0,
			Math.max(config.customNearSpacing(), config.customFarSpacing()) / 10.0,
			config.customScaleMin(),
			Math.max(config.customScaleMin(), config.customScaleMax()),
			config.customOuterBoost());
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
		int cover = config.fakeGround() ? config.grassTufts() : 0;
		ForestLayout.Result layout = ForestLayout.compute(plot, style, config.clearingMargin(), config.density(),
			config.maxObjects(), config.undergrowth(), cover, ForestModels.COVER, config.seed());

		// Tiles outside the plot have no height data (0); lift objects there to the plot's ground level
		int groundY = referenceHeight(built, scene);

		fogRgb = skyRgb();
		hazeSpan = 1;
		if (layout.dist != null)
		{
			for (ForestLayout.Spot spot : layout.spots)
			{
				hazeSpan = Math.max(hazeSpan, spot.dist - config.hazeStart());
			}
		}

		if (config.fakeGround() && layout.dist != null)
		{
			spawnGround(scene, groundY, plot, built, layout.dist, style);
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
		coverCount = layout.cover;
		spawned = true;
		spawnMillis = (System.nanoTime() - start) / 1_000_000;
		describe(layout, style);

		log.debug("{} spawned {} trees + {} undergrowth + {} grass + {} ground in {} ms ({} models, {} candidates, "
				+ "fog rgb {}, haze span {})", activePreset, treeCount, undergrowthCount, coverCount, groundCount,
			spawnMillis, models.size(), layout.candidates, Integer.toHexString(fogRgb), hazeSpan);
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

	/** Sky colour, used as the fog colour. Falls back to a light blue like the POH sky. */
	private int skyRgb()
	{
		int sky = client.getSkyboxColor();
		return (sky & 0xFFFFFF) != 0 ? sky & 0xFFFFFF : DEFAULT_SKY;
	}

	/** Fraction of fog (0..1, in steps of 0.1) for something this many tiles from the plot. */
	private double hazeFor(int dist)
	{
		double strength = config.haze() / 100.0;
		if (strength <= 0)
		{
			return 0;
		}
		double t = Math.max(0, Math.min(1, (dist - config.hazeStart()) / (double) hazeSpan));
		return Math.round(strength * t * 10) / 10.0;
	}

	/**
	 * Flat grass-coloured quads over every empty chunk, four per chunk so the haze can step in 4
	 * tile bands. Model 7768 is a 384x384 flat quad (two faces, colour 0) that magic trees use as
	 * a base; 171/128 * 384 = 513 units covers a 4x4 tile cell with a hair of overlap.
	 */
	private void spawnGround(Scene scene, int groundY, boolean[][] plot, boolean[][] built, int[][] dist,
		ForestStyle style)
	{
		int[][] chunks = scene.getInstanceTemplateChunks()[0];
		short base = groundColor(scene, plot, built);
		base = adjustColor(base, config.groundSaturation(), config.groundLightness() - style.groundDarken);
		groundColor = String.format("hsl %d/%d/%d", (base >> 10) & 63, (base >> 7) & 7, base & 127);

		if (client.loadModelData(GROUND_QUAD_MODEL) == null)
		{
			log.debug("Ground quad model {} did not load", GROUND_QUAD_MODEL);
			return;
		}

		int cell = Constants.CHUNK_SIZE / 2;
		for (int cx = 0; cx < chunks.length; cx++)
		{
			for (int cy = 0; cy < chunks[cx].length; cy++)
			{
				if (chunks[cx][cy] != -1)
				{
					continue;
				}
				for (int ox = 0; ox < 2; ox++)
				{
					for (int oy = 0; oy < 2; oy++)
					{
						// Cell centre is the corner shared by its four middle tiles
						int tx = cx * Constants.CHUNK_SIZE + ox * cell + cell / 2 - 1;
						int ty = cy * Constants.CHUNK_SIZE + oy * cell + cell / 2 - 1;
						LocalPoint at = LocalPoint.fromScene(tx, ty, scene)
							.plus(Perspective.LOCAL_HALF_TILE_SIZE, Perspective.LOCAL_HALF_TILE_SIZE);
						short shaded = Haze.blend(base, fogRgb, hazeFor(dist[tx][ty]));
						RuneLiteObject object = client.createRuneLiteObject();
						object.setModel(groundModel(shaded, groundY - Perspective.getTileHeight(client, at, 0)));
						object.setLocation(at, 0);
						object.setActive(true);
						objects.add(object);
						groundCount++;
					}
				}
			}
		}
	}

	private Model groundModel(short color, int lift)
	{
		// A hair below the plot's own ground so the real tiles win where they meet
		String key = "ground|" + color + "|" + lift;
		return models.computeIfAbsent(key, k ->
		{
			ModelData data = client.loadModelData(GROUND_QUAD_MODEL).shallowCopy().cloneVertices().cloneColors();
			data.recolor((short) 0, color);
			// Negative x scale mirrors the quad so its faces point up and are lit from above
			return data.scale(-171, 128, 171).translate(0, lift + 2, 0)
				.light(GROUND_AMBIENT, ModelData.DEFAULT_CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y,
					ModelData.DEFAULT_Z);
		});
	}

	/** Colour of the real grass: the first open-grass tile paint, else a fallback. */
	private short groundColor(Scene scene, boolean[][] plot, boolean[][] built)
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
				if ((rgb & 0xFFFFFF) != 0)
				{
					return JagexColor.rgbToHSL(rgb & 0xFFFFFF, 1.0);
				}
			}
		}
		return FALLBACK_GRASS;
	}

	private static short adjustColor(short color, int saturationSteps, int lightnessDelta)
	{
		int hue = (color >> 10) & 63;
		int saturation = Math.max(0, Math.min(7, ((color >> 7) & 7) + saturationSteps));
		int lightness = Math.max(0, Math.min(127, (color & 127) + lightnessDelta));
		return JagexColor.packHSL(hue, saturation, lightness);
	}

	private Model modelFor(ForestLayout.Spot spot, ForestStyle style, int lift)
	{
		double haze = hazeFor(spot.dist);
		int scale = Math.max(SCALE_STEP, Math.round(spot.scalePct / (float) SCALE_STEP) * SCALE_STEP);
		// Only trees are stretched; undergrowth and grass keep their natural proportions
		int height = spot.undergrowth ? 100 : style.heightPct;
		String key = spot.species.name + "|" + scale + "|" + height + "|" + haze + "|" + lift;

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
		if (haze > 0)
		{
			short[] colors = data.getFaceColors();
			for (int i = 0; i < colors.length; i++)
			{
				colors[i] = Haze.blend(colors[i], fogRgb, haze);
			}
		}

		int s = 128 * scale / 100;
		Model model = data.scale(s, s * height / 100, s).translate(0, lift, 0).light();
		models.put(key, model);
		return model;
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
		coverCount = 0;
	}

	@Provides
	PohForestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohForestConfig.class);
	}
}
