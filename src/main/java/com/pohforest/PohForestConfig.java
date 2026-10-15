package com.pohforest;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(PohForestConfig.GROUP)
public interface PohForestConfig extends Config
{
	String GROUP = "poh-forest";

	@ConfigSection(
		name = "Custom preset",
		description = "Used when the preset is Custom",
		position = 10,
		closedByDefault = true
	)
	String customSection = "custom";

	@ConfigItem(
		keyName = "preset",
		name = "Preset",
		description = "Which forest to grow around the house",
		position = 0
	)
	default ForestPreset preset()
	{
		return ForestPreset.DENSE_PINE;
	}

	@ConfigItem(
		keyName = "seed",
		name = "Seed",
		description = "The same seed always grows the same forest",
		position = 1
	)
	default int seed()
	{
		return 1337;
	}

	@ConfigItem(
		keyName = "clearingMargin",
		name = "Clearing margin",
		description = "Tiles kept clear between the walkable plot and the first trees",
		position = 2
	)
	@Range(min = 0, max = 10)
	default int clearingMargin()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "density",
		name = "Density %",
		description = "100 is the preset's own spacing",
		position = 3
	)
	@Range(min = 25, max = 300)
	default int density()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "maxObjects",
		name = "Object budget",
		description = "Hard cap on trees plus undergrowth. The outer band is thinned first.",
		position = 4
	)
	@Range(min = 50, max = 3000)
	default int maxObjects()
	{
		return 700;
	}

	@ConfigItem(
		keyName = "undergrowth",
		name = "Undergrowth",
		description = "Bushes, ferns and rocks between the trees",
		position = 5
	)
	default boolean undergrowth()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fakeGround",
		name = "Fake ground",
		description = "Lays a flat grass-coloured surface over the empty void so trees stand on something",
		position = 5
	)
	default boolean fakeGround()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groundLightness",
		name = "Ground lightness",
		description = "Brighten or darken the fake ground to match the real grass",
		position = 5
	)
	@Range(min = -60, max = 60)
	default int groundLightness()
	{
		return -6;
	}

	@ConfigItem(
		keyName = "groundSaturation",
		name = "Ground saturation",
		description = "Steps of colour saturation for the fake ground; the lit quad looks washed out next to real grass",
		position = 5
	)
	@Range(min = -3, max = 3)
	default int groundSaturation()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "grassTufts",
		name = "Grass tufts",
		description = "How many small grass objects to scatter over the fake ground (0 = none)",
		position = 5
	)
	@Range(min = 0, max = 3000)
	default int grassTufts()
	{
		return 1200;
	}

	@ConfigItem(
		keyName = "haze",
		name = "Haze %",
		description = "Fades far trees and ground toward the sky colour. Stands in for GPU fog, which is not applied in the POH.",
		position = 5
	)
	@Range(min = 0, max = 100)
	default int haze()
	{
		return 75;
	}

	@ConfigItem(
		keyName = "hazeStart",
		name = "Haze starts (tiles)",
		description = "Distance from the plot where the haze begins",
		position = 5
	)
	@Range(min = 0, max = 40)
	default int hazeStart()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "cycleKey",
		name = "Cycle preset hotkey",
		description = "Switches to the next preset",
		position = 6
	)
	default Keybind cycleKey()
	{
		return new Keybind(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "debugOverlay",
		name = "Debug overlay",
		description = "Shows preset, object count, spawn time and the density curve",
		position = 7
	)
	default boolean debugOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customPalette",
		name = "Palette",
		description = "Which preset's trees, undergrowth and recolour to use",
		section = customSection,
		position = 0
	)
	default ForestPreset customPalette()
	{
		return ForestPreset.DENSE_PINE;
	}

	@ConfigItem(
		keyName = "customNearSpacing",
		name = "Near spacing (tenths of tile)",
		description = "Distance between trees next to the house",
		section = customSection,
		position = 1
	)
	@Range(min = 10, max = 100)
	default int customNearSpacing()
	{
		return 24;
	}

	@ConfigItem(
		keyName = "customFarSpacing",
		name = "Far spacing (tenths of tile)",
		description = "Distance between trees in the outer band",
		section = customSection,
		position = 2
	)
	@Range(min = 10, max = 150)
	default int customFarSpacing()
	{
		return 45;
	}

	@ConfigItem(
		keyName = "customScaleMin",
		name = "Min scale %",
		description = "Smallest tree size",
		section = customSection,
		position = 3
	)
	@Range(min = 40, max = 200)
	default int customScaleMin()
	{
		return 85;
	}

	@ConfigItem(
		keyName = "customScaleMax",
		name = "Max scale %",
		description = "Largest tree size",
		section = customSection,
		position = 4
	)
	@Range(min = 40, max = 250)
	default int customScaleMax()
	{
		return 115;
	}

	@ConfigItem(
		keyName = "customOuterBoost",
		name = "Outer band scale %",
		description = "How much larger trees get in the outer band",
		section = customSection,
		position = 5
	)
	@Range(min = 100, max = 250)
	default int customOuterBoost()
	{
		return 140;
	}
}
