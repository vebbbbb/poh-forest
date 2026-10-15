package com.pohforest;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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

	// Willow tree (ObjectID.WILLOWTREE 10819): single model, no recolours
	private static final int TREE_MODEL_ID = 8131;

	private static final int BAND_TILES = 8;
	private static final double DENSITY = 0.75;
	private static final int MAX_TREES = 150;
	private static final long SEED = 1337;

	@Inject
	private Client client;

	private boolean inPoh;
	private final List<RuneLiteObject> trees = new ArrayList<>();
	private boolean spawned;
	private int spawnedPlane;

	@Override
	protected void shutDown()
	{
		despawnTrees();
		inPoh = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// The scene is rebuilt on LOADING; objects from the old scene must go
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			despawnTrees();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean nowInPoh = isInPoh();
		if (nowInPoh != inPoh)
		{
			inPoh = nowInPoh;
			log.info("POH {}", inPoh ? "entered" : "left");
		}

		if (!inPoh)
		{
			despawnTrees();
			return;
		}

		// Stairs change the plane without a scene reload, and the footprint is per plane
		int plane = client.getTopLevelWorldView().getPlane();
		if (spawned && plane != spawnedPlane)
		{
			despawnTrees();
		}
		if (!spawned)
		{
			spawnTrees(plane);
		}
	}

	private boolean isInPoh()
	{
		Player player = client.getLocalPlayer();
		if (player == null || !client.isInInstancedRegion())
		{
			return false;
		}
		WorldPoint wp = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return wp != null && POH_REGIONS.contains(wp.getRegionID());
	}

	private void spawnTrees(int plane)
	{
		Scene scene = client.getTopLevelWorldView().getScene();
		int[][] chunks = scene.getInstanceTemplateChunks()[plane];
		List<ForestLayout.Spot> spots = ForestLayout.compute(chunks, BAND_TILES, DENSITY, MAX_TREES, SEED);

		for (ForestLayout.Spot spot : spots)
		{
			LocalPoint base = LocalPoint.fromScene(spot.sceneX, spot.sceneY, scene);
			RuneLiteObject tree = client.createRuneLiteObject();
			tree.setModel(client.loadModel(TREE_MODEL_ID));
			tree.setLocation(base.plus(spot.jitterX, spot.jitterY), plane);
			tree.setOrientation(spot.orientation);
			tree.setActive(true);
			trees.add(tree);
		}
		spawned = true;
		spawnedPlane = plane;
		log.debug("Spawned {} trees on plane {}", trees.size(), plane);
	}

	private void despawnTrees()
	{
		for (RuneLiteObject tree : trees)
		{
			tree.setActive(false);
		}
		trees.clear();
		spawned = false;
	}

	@Provides
	PohForestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohForestConfig.class);
	}
}
