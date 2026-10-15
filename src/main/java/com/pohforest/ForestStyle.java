package com.pohforest;

import java.util.List;

/** Everything that defines how one forest looks. A preset is one of these. */
final class ForestStyle
{
	final List<Species> trees;
	final List<Species> undergrowth;
	/** Tile spacing between tree centres right outside the clearing margin... */
	final double nearSpacing;
	/** ...growing to this at {@link #falloffTiles} from the house and beyond. */
	final double farSpacing;
	final int falloffTiles;
	/** Tree scale as a percentage of the model's normal size. */
	final int scaleMinPct;
	final int scaleMaxPct;
	/** Scale factor (percent) reached in the outer band, so the far forest reads as a wall. */
	final int outerBoostPct;
	/** Null for no recolour. */
	final Recolour recolour;
	/** Tile spacing between undergrowth objects; 0 turns undergrowth off. */
	final double undergrowthSpacing;

	ForestStyle(List<Species> trees, List<Species> undergrowth, double nearSpacing, double farSpacing,
		int falloffTiles, int scaleMinPct, int scaleMaxPct, int outerBoostPct, Recolour recolour,
		double undergrowthSpacing)
	{
		this.trees = trees;
		this.undergrowth = undergrowth;
		this.nearSpacing = nearSpacing;
		this.farSpacing = farSpacing;
		this.falloffTiles = falloffTiles;
		this.scaleMinPct = scaleMinPct;
		this.scaleMaxPct = scaleMaxPct;
		this.outerBoostPct = outerBoostPct;
		this.recolour = recolour;
		this.undergrowthSpacing = undergrowthSpacing;
	}

	/** Same palette and recolour, different shape: used by the CUSTOM preset's sliders. */
	ForestStyle withShape(double nearSpacing, double farSpacing, int scaleMinPct, int scaleMaxPct,
		int outerBoostPct, double undergrowthSpacing)
	{
		return new ForestStyle(trees, undergrowth, nearSpacing, farSpacing, falloffTiles, scaleMinPct,
			scaleMaxPct, outerBoostPct, recolour, undergrowthSpacing);
	}
}
