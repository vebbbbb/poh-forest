package com.pohforest;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum ForestPreset
{
	DENSE_PINE("Dense pine",
		new ForestStyle(list(ForestModels.PINE_A, ForestModels.PINE_B, ForestModels.PINE_C),
			list(ForestModels.FERN_A, ForestModels.FERN_B, ForestModels.ROCKS, ForestModels.BUSH_B),
			2.2, 4.0, 40, 85, 115, 140, null, 3.0)),
	MIXED_DECIDUOUS("Mixed deciduous",
		new ForestStyle(list(ForestModels.TREE_A, ForestModels.TREE_B, ForestModels.OAK, ForestModels.WILLOW,
			ForestModels.MAPLE),
			list(ForestModels.BUSH_A, ForestModels.BUSH_B, ForestModels.FERN_A, ForestModels.FERN_B, ForestModels.STUMP),
			2.4, 4.5, 40, 85, 115, 140, null, 3.0)),
	// Untextured foliage only: textured leaves ignore hue, so those models can't be recoloured
	AUTUMN("Autumn",
		new ForestStyle(list(ForestModels.TREE_FLAT_A, ForestModels.TREE_FLAT_B, ForestModels.TREE_FLAT_C,
			ForestModels.TREE_FLAT_D),
			list(ForestModels.BUSH_A, ForestModels.FERN_A, ForestModels.STUMP),
			2.4, 4.5, 40, 85, 115, 140, new Recolour(11, 24, 2, 3, 4, 6, 9), 3.5)),
	MAGIC_GROVE("Magic grove",
		new ForestStyle(list(ForestModels.MAGIC_A, ForestModels.YEW_A, ForestModels.YEW_B, ForestModels.MAGIC_B,
			ForestModels.MAGIC_C),
			list(ForestModels.FERN_A, ForestModels.FERN_C),
			3.5, 6.0, 45, 110, 150, 150, null, 4.0)),
	DEAD_SPOOKY("Dead / spooky",
		new ForestStyle(list(ForestModels.DEAD_A, ForestModels.DEAD_B, ForestModels.DEAD_C, ForestModels.DEAD_YEW,
			ForestModels.DEAD_MAPLE, ForestModels.DEAD_MAGIC),
			list(ForestModels.STUMP, ForestModels.ROCKS),
			2.6, 4.8, 40, 85, 115, 130, null, 4.0)),
	JUNGLE("Jungle",
		new ForestStyle(list(ForestModels.JUNGLE_A, ForestModels.JUNGLE_B, ForestModels.JUNGLE_C,
			ForestModels.PALM_A, ForestModels.PALM_B, ForestModels.TROPICAL_A, ForestModels.TROPICAL_B),
			list(ForestModels.JUNGLE_PLANT_A, ForestModels.JUNGLE_PLANT_B, ForestModels.FERN_A, ForestModels.FERN_B,
				ForestModels.BUSH_B),
			2.2, 4.0, 40, 85, 120, 140, null, 2.5)),
	/** Takes the palette of the "custom palette" setting and shapes it with the individual sliders. */
	CUSTOM("Custom", null);

	private final String name;
	private final ForestStyle style;

	ForestPreset(String name, ForestStyle style)
	{
		this.name = name;
		this.style = style;
	}

	private static List<Species> list(Species... species)
	{
		return Arrays.asList(species);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
