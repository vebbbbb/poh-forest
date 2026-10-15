package com.pohforest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;

/**
 * Works out where trees go around the house. Pure computation: the house footprint comes from the
 * instance template chunks (one 8x8 tile chunk per room), so it follows the player's layout.
 */
final class ForestLayout
{
	static final class Spot
	{
		final int sceneX;
		final int sceneY;
		final int jitterX;
		final int jitterY;
		final int orientation;

		Spot(int sceneX, int sceneY, int jitterX, int jitterY, int orientation)
		{
			this.sceneX = sceneX;
			this.sceneY = sceneY;
			this.jitterX = jitterX;
			this.jitterY = jitterY;
			this.orientation = orientation;
		}
	}

	private static final int CHUNK = Constants.CHUNK_SIZE;
	private static final int SCENE = Constants.SCENE_SIZE;
	private static final int NO_CHUNK = -1;

	private ForestLayout()
	{
	}

	/**
	 * @param templateChunks instance template chunks of one plane, indexed [chunkX][chunkY]; -1 = empty
	 * @param band           how many tiles outside the outermost rooms the forest extends
	 * @param density        0..1 chance that an eligible 2x2 cell gets a tree
	 * @param maxTrees       hard cap on the number of trees
	 */
	static List<Spot> compute(int[][] templateChunks, int band, double density, int maxTrees, long seed)
	{
		boolean[][] house = new boolean[SCENE][SCENE];
		boolean[][] near = new boolean[SCENE][SCENE];
		for (int cx = 0; cx < templateChunks.length; cx++)
		{
			for (int cy = 0; cy < templateChunks[cx].length; cy++)
			{
				if (templateChunks[cx][cy] == NO_CHUNK)
				{
					continue;
				}
				int x0 = cx * CHUNK;
				int y0 = cy * CHUNK;
				for (int x = Math.max(0, x0 - band); x < Math.min(SCENE, x0 + CHUNK + band); x++)
				{
					for (int y = Math.max(0, y0 - band); y < Math.min(SCENE, y0 + CHUNK + band); y++)
					{
						near[x][y] = true;
					}
				}
				for (int x = x0; x < x0 + CHUNK; x++)
				{
					for (int y = y0; y < y0 + CHUNK; y++)
					{
						house[x][y] = true;
					}
				}
			}
		}

		List<Spot> spots = new ArrayList<>();
		// Step 2 tiles so 2x2 trees don't overlap; keep a margin so objects stay inside the scene
		for (int x = 2; x < SCENE - 2; x += 2)
		{
			for (int y = 2; y < SCENE - 2; y += 2)
			{
				if (!near[x][y] || house[x][y] || house[x + 1][y] || house[x][y + 1] || house[x + 1][y + 1])
				{
					continue;
				}
				// Seeded per tile, so the layout is the same on every visit
				Random rng = new Random(seed * 31 * 31 + x * 31 + y);
				if (rng.nextDouble() >= density)
				{
					continue;
				}
				int half = Perspective.LOCAL_TILE_SIZE / 4;
				spots.add(new Spot(x, y, rng.nextInt(2 * half) - half, rng.nextInt(2 * half) - half, rng.nextInt(2048)));
			}
		}

		if (spots.size() > maxTrees)
		{
			// Thin evenly rather than cutting off one side of the house
			Collections.shuffle(spots, new Random(seed));
			spots = new ArrayList<>(spots.subList(0, maxTrees));
		}
		return spots;
	}
}
