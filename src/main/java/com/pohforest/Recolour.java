package com.pohforest;

/**
 * Hue remap for foliage. Face colours are Jagex 16-bit HSL: hue in bits 10-15, saturation in bits
 * 7-9, lightness in bits 0-6. Faces whose hue is in [hueMin, hueMax] get one of targetHues, chosen
 * per tree.
 */
final class Recolour
{
	final int hueMin;
	final int hueMax;
	final int[] targetHues;

	Recolour(int hueMin, int hueMax, int... targetHues)
	{
		this.hueMin = hueMin;
		this.hueMax = hueMax;
		this.targetHues = targetHues;
	}

	short map(short color, int variant)
	{
		int c = color & 0xFFFF;
		int hue = (c >> 10) & 63;
		if (hue < hueMin || hue > hueMax)
		{
			return color;
		}
		int target = targetHues[variant % targetHues.length];
		return (short) ((c & 0x3FF) | (target << 10));
	}
}
