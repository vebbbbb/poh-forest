package com.pohforest;

import com.google.inject.Provides;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
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

	@Inject
	private Client client;

	private boolean inPoh;
	private RuneLiteObject tree;

	@Override
	protected void shutDown()
	{
		despawnTree();
		inPoh = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// The scene is rebuilt on LOADING; objects from the old scene must go
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			despawnTree();
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

		if (inPoh && tree == null)
		{
			spawnTree();
		}
		else if (!inPoh && tree != null)
		{
			despawnTree();
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

	private void spawnTree()
	{
		LocalPoint here = client.getLocalPlayer().getLocalLocation();
		tree = client.createRuneLiteObject();
		tree.setModel(client.loadModel(TREE_MODEL_ID));
		tree.setLocation(here.dx(2 * 128), client.getTopLevelWorldView().getPlane());
		tree.setActive(true);
	}

	private void despawnTree()
	{
		if (tree != null)
		{
			tree.setActive(false);
			tree = null;
		}
	}

	@Provides
	PohForestConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohForestConfig.class);
	}
}
