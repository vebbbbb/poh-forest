package com.pohforest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;

/**
 * Works out where trees go. Pure computation apart from {@link #builtTiles}, so a layout is
 * reproducible from (house, style, config, seed).
 *
 * Trees only grow in empty (void) chunks outside the walkable plot. Built chunks, found from a wall, a
 * decorative object or a game object, are used for the debug map and the ground height reference.
 * Plain grass filler chunks hold none of those but are walkable, so they stay clear too.
 */
final class ForestLayout
{
	static final class Spot
	{
		final int tileX;
		final int tileY;
		/** Offset from the tile centre in local units, -51..51. */
		final int offX;
		final int offY;
		final Species species;
		final boolean undergrowth;
		final int scalePct;
		final int yaw;
		final int variant;

		Spot(int tileX, int tileY, int offX, int offY, Species species, boolean undergrowth, int scalePct,
			int yaw, int variant)
		{
			this.tileX = tileX;
			this.tileY = tileY;
			this.offX = offX;
			this.offY = offY;
			this.species = species;
			this.undergrowth = undergrowth;
			this.scalePct = scalePct;
			this.yaw = yaw;
			this.variant = variant;
		}
	}

	static final class Result
	{
		final List<Spot> spots = new ArrayList<>();
		int trees;
		int undergrowth;
		int candidates;
		/** minX, maxX, minY, maxY of built tiles in scene tiles; null if nothing is built. */
		int[] houseBounds;
	}

	private static final int SCENE = Constants.SCENE_SIZE;
	private static final int CHUNK = Constants.CHUNK_SIZE;
	/** Keep objects this many tiles inside the scene edge. */
	private static final int EDGE = 2;
	private static final double UNDERGROWTH_TREE_CLEARANCE = 1.1;
	private static final double TREE_BUDGET_SHARE = 0.75;

	private ForestLayout()
	{
	}

	/** Marks every tile of every chunk that has built content. If gridLog is given, draws the chunk map. */
	static boolean[][] builtTiles(Scene scene, List<String> gridLog)
	{
		boolean[][] built = new boolean[SCENE][SCENE];
		Tile[][][] tiles = scene.getTiles();
		int[][][] templates = scene.getInstanceTemplateChunks();
		int chunks = SCENE / CHUNK;
		StringBuilder[] rows = new StringBuilder[chunks];
		for (int cy = 0; cy < chunks; cy++)
		{
			rows[cy] = new StringBuilder();
		}

		for (int cx = 0; cx < chunks; cx++)
		{
			for (int cy = 0; cy < chunks; cy++)
			{
				boolean any = chunkHasContent(tiles, cx, cy);
				if (any)
				{
					for (int x = cx * CHUNK; x < (cx + 1) * CHUNK; x++)
					{
						for (int y = cy * CHUNK; y < (cy + 1) * CHUNK; y++)
						{
							built[x][y] = true;
						}
					}
				}
				boolean template = templates != null && templates.length > 0 && templates[0][cx][cy] != -1;
				rows[cy].append(any ? '#' : template ? '.' : ' ');
			}
		}

		if (gridLog != null)
		{
			// North at the top
			for (int cy = chunks - 1; cy >= 0; cy--)
			{
				gridLog.add(rows[cy].toString());
			}
		}
		return built;
	}

	/**
	 * Every tile of the walkable plot: chunks that exist in the template on any plane, built or
	 * plain grass. The forest only grows outside this, in the empty void chunks.
	 */
	static boolean[][] plotTiles(Scene scene)
	{
		boolean[][] plot = new boolean[SCENE][SCENE];
		int[][][] templates = scene.getInstanceTemplateChunks();
		int chunks = SCENE / CHUNK;
		for (int[][] plane : templates)
		{
			for (int cx = 0; cx < chunks; cx++)
			{
				for (int cy = 0; cy < chunks; cy++)
				{
					if (plane[cx][cy] == -1)
					{
						continue;
					}
					for (int x = cx * CHUNK; x < (cx + 1) * CHUNK; x++)
					{
						for (int y = cy * CHUNK; y < (cy + 1) * CHUNK; y++)
						{
							plot[x][y] = true;
						}
					}
				}
			}
		}
		return plot;
	}

	private static boolean chunkHasContent(Tile[][][] tiles, int cx, int cy)
	{
		for (int z = 0; z < tiles.length; z++)
		{
			for (int x = cx * CHUNK; x < (cx + 1) * CHUNK; x++)
			{
				for (int y = cy * CHUNK; y < (cy + 1) * CHUNK; y++)
				{
					Tile tile = tiles[z][x][y];
					if (tile == null)
					{
						continue;
					}
					if (tile.getWallObject() != null || tile.getDecorativeObject() != null)
					{
						return true;
					}
					for (GameObject object : tile.getGameObjects())
					{
						if (object != null)
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static final class Candidate
	{
		int x;
		int y;
		int dist;
		double px;
		double py;
		double key;
	}

	/**
	 * @param margin     clear tiles kept around every built tile
	 * @param densityPct 100 = the style's spacing; 200 = twice as many trees
	 * @param maxObjects hard budget for trees plus undergrowth; the outer band is cut first
	 */
	static Result compute(boolean[][] built, ForestStyle style, int margin, int densityPct, int maxObjects,
		boolean withUndergrowth, long seed)
	{
		Result result = new Result();

		int[][] dist = new int[SCENE][SCENE];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		int minX = SCENE;
		int maxX = -1;
		int minY = SCENE;
		int maxY = -1;
		for (int x = 0; x < SCENE; x++)
		{
			for (int y = 0; y < SCENE; y++)
			{
				if (built[x][y])
				{
					dist[x][y] = 0;
					queue.add(new int[]{x, y});
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
				else
				{
					dist[x][y] = -1;
				}
			}
		}
		if (queue.isEmpty())
		{
			return result;
		}
		result.houseBounds = new int[]{minX, maxX, minY, maxY};

		// Chebyshev distance to the nearest built tile (8-neighbour BFS)
		while (!queue.isEmpty())
		{
			int[] p = queue.poll();
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int nx = p[0] + dx;
					int ny = p[1] + dy;
					if (nx < 0 || ny < 0 || nx >= SCENE || ny >= SCENE || dist[nx][ny] != -1)
					{
						continue;
					}
					dist[nx][ny] = dist[p[0]][p[1]] + 1;
					queue.add(new int[]{nx, ny});
				}
			}
		}

		// One jittered candidate per free tile. Random numbers are per tile so the layout does not
		// depend on iteration order.
		List<Candidate> candidates = new ArrayList<>();
		for (int x = EDGE; x < SCENE - EDGE; x++)
		{
			for (int y = EDGE; y < SCENE - EDGE; y++)
			{
				if (dist[x][y] <= margin)
				{
					continue;
				}
				Random tileRng = new Random(seed * 1_000_003L + x * 131L + y);
				Candidate c = new Candidate();
				c.x = x;
				c.y = y;
				c.dist = dist[x][y];
				c.px = x + 0.1 + 0.8 * tileRng.nextDouble();
				c.py = y + 0.1 + 0.8 * tileRng.nextDouble();
				c.key = tileRng.nextDouble();
				candidates.add(c);
			}
		}
		// Nearest first, so the budget cuts the outer band and the house surroundings fill first
		candidates.sort(Comparator.<Candidate>comparingInt(c -> c.dist).thenComparingDouble(c -> c.key));
		result.candidates = candidates.size();

		double density = Math.max(0.1, densityPct / 100.0);
		Random rng = new Random(seed);
		int treeBudget = withUndergrowth && style.undergrowthSpacing > 0
			? (int) (maxObjects * TREE_BUDGET_SHARE) : maxObjects;

		SpatialIndex treeIndex = new SpatialIndex();
		int totalTreeWeight = totalWeight(style.trees);
		for (Candidate c : candidates)
		{
			if (result.trees >= treeBudget)
			{
				break;
			}
			double t = blend(c.dist, margin, style.falloffTiles);
			double spacing = (style.nearSpacing + (style.farSpacing - style.nearSpacing) * t) / density;
			if (treeIndex.anyWithin(c.px, c.py, spacing))
			{
				continue;
			}
			treeIndex.add(c.px, c.py);

			Species species = pick(style.trees, totalTreeWeight, rng);
			double base = style.scaleMinPct + (style.scaleMaxPct - style.scaleMinPct) * rng.nextDouble();
			int scale = (int) Math.round(base * (100 + (style.outerBoostPct - 100) * t) / 100.0);
			result.spots.add(spot(c, species, false, scale, rng));
			result.trees++;
		}

		if (withUndergrowth && style.undergrowthSpacing > 0 && !style.undergrowth.isEmpty())
		{
			SpatialIndex shrubIndex = new SpatialIndex();
			int totalShrubWeight = totalWeight(style.undergrowth);
			double spacing = style.undergrowthSpacing / density;
			for (Candidate c : candidates)
			{
				if (result.trees + result.undergrowth >= maxObjects)
				{
					break;
				}
				if (shrubIndex.anyWithin(c.px, c.py, spacing)
					|| treeIndex.anyWithin(c.px, c.py, UNDERGROWTH_TREE_CLEARANCE))
				{
					continue;
				}
				shrubIndex.add(c.px, c.py);

				Species species = pick(style.undergrowth, totalShrubWeight, rng);
				int scale = 80 + rng.nextInt(41);
				result.spots.add(spot(c, species, true, scale, rng));
				result.undergrowth++;
			}
		}

		Collections.shuffle(result.spots, new Random(seed ^ 0x5DEECE66DL));
		return result;
	}

	private static Spot spot(Candidate c, Species species, boolean undergrowth, int scalePct, Random rng)
	{
		int offX = (int) Math.round((c.px - c.x - 0.5) * 128);
		int offY = (int) Math.round((c.py - c.y - 0.5) * 128);
		return new Spot(c.x, c.y, offX, offY, species, undergrowth, scalePct, rng.nextInt(2048), rng.nextInt(1000));
	}

	/** 0 at the clearing margin, 1 at falloff tiles beyond it. */
	static double blend(int dist, int margin, int falloff)
	{
		return Math.max(0, Math.min(1, (dist - margin) / (double) Math.max(1, falloff)));
	}

	private static int totalWeight(List<Species> list)
	{
		int total = 0;
		for (Species s : list)
		{
			total += s.weight;
		}
		return total;
	}

	private static Species pick(List<Species> list, int totalWeight, Random rng)
	{
		int roll = rng.nextInt(totalWeight);
		for (Species s : list)
		{
			roll -= s.weight;
			if (roll < 0)
			{
				return s;
			}
		}
		return list.get(list.size() - 1);
	}

	/** Bucketed points for "is anything within r of here" queries. */
	private static final class SpatialIndex
	{
		private static final double CELL = 2.0;
		private final int cells = (int) Math.ceil(SCENE / CELL) + 1;
		private final List<double[]>[][] grid;

		@SuppressWarnings("unchecked")
		SpatialIndex()
		{
			grid = new List[cells][cells];
		}

		void add(double x, double y)
		{
			int cx = (int) (x / CELL);
			int cy = (int) (y / CELL);
			if (grid[cx][cy] == null)
			{
				grid[cx][cy] = new ArrayList<>();
			}
			grid[cx][cy].add(new double[]{x, y});
		}

		boolean anyWithin(double x, double y, double r)
		{
			int reach = (int) Math.ceil(r / CELL);
			int cx = (int) (x / CELL);
			int cy = (int) (y / CELL);
			double r2 = r * r;
			for (int gx = Math.max(0, cx - reach); gx <= Math.min(cells - 1, cx + reach); gx++)
			{
				for (int gy = Math.max(0, cy - reach); gy <= Math.min(cells - 1, cy + reach); gy++)
				{
					List<double[]> bucket = grid[gx][gy];
					if (bucket == null)
					{
						continue;
					}
					for (double[] p : bucket)
					{
						double dx = p[0] - x;
						double dy = p[1] - y;
						if (dx * dx + dy * dy < r2)
						{
							return true;
						}
					}
				}
			}
			return false;
		}
	}
}
