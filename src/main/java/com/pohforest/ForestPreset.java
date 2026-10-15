package com.pohforest;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum ForestPreset
{
	DENSE_PINE("Dense pine",
		new ForestStyle(
			list(ForestModels.PINE_A, ForestModels.PINE_B, ForestModels.PINE_C, ForestModels.PINE_D,
				ForestModels.PINE_E, ForestModels.PINE_F),
			list(ForestModels.SAPLING_A, ForestModels.SAPLING_B, ForestModels.FERN_A, ForestModels.FERN_B,
				ForestModels.ROCKS, ForestModels.BUSH_B),
			2.2, 4.0, 40, 85, 115, 140, 3.0, 100, 0)),
	/** Towering, tightly packed trunks over a shaded fern-and-mushroom floor, after the Varlamore redwood forest. */
	REDWOOD("Redwood",
		new ForestStyle(
			list(ForestModels.REDWOOD_A, ForestModels.REDWOOD_B, ForestModels.PINE_A, ForestModels.PINE_C),
			list(ForestModels.FERN_C, ForestModels.FERN_D, ForestModels.FERN_E, ForestModels.FERN_F,
				ForestModels.MUSHROOM_A, ForestModels.MUSHROOM_B, ForestModels.MUSHROOM_C, ForestModels.MUSHROOM_D,
				ForestModels.STUMP),
			1.9, 4.2, 30, 140, 180, 150, 2.4, 190, 12)),
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
