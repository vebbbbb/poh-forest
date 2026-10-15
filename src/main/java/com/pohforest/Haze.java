package com.pohforest;

import net.runelite.api.JagexColor;

/**
 * Plugin-side distance fog. The GPU plugin's own fog is not available in the POH here, so far
 * trees and ground are blended toward the sky colour when their models are built.
 *
 * Jagex HSL16: hue bits 10-15, saturation bits 7-9, lightness bits 0-6. The palette uses
 * hue/64 + 1/128 and sat/8 + 1/16, which is what the conversion below mirrors.
 */
final class Haze
{
	private Haze()
	{
	}

	/** Blends an HSL16 colour toward the fog RGB; amount 0 returns it unchanged. */
	static short blend(short hsl, int fogRgb, double amount)
	{
		if (amount <= 0)
		{
			return hsl;
		}
		int rgb = toRgb(hsl);
		int r = mix((rgb >> 16) & 0xFF, (fogRgb >> 16) & 0xFF, amount);
		int g = mix((rgb >> 8) & 0xFF, (fogRgb >> 8) & 0xFF, amount);
		int b = mix(rgb & 0xFF, fogRgb & 0xFF, amount);
		return JagexColor.rgbToHSL((r << 16) | (g << 8) | b, 1.0);
	}

	private static int mix(int from, int to, double amount)
	{
		return (int) Math.round(from + (to - from) * amount);
	}

	static int toRgb(short hsl)
	{
		double h = ((hsl >> 10) & 63) / 64.0 + 0.0078125;
		double s = ((hsl >> 7) & 7) / 8.0 + 0.0625;
		double l = (hsl & 127) / 128.0;

		double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
		double p = 2 * l - q;
		int r = (int) (channel(p, q, h + 1.0 / 3) * 256);
		int g = (int) (channel(p, q, h) * 256);
		int b = (int) (channel(p, q, h - 1.0 / 3) * 256);
		return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static double channel(double p, double q, double t)
	{
		if (t < 0)
		{
			t += 1;
		}
		if (t > 1)
		{
			t -= 1;
		}
		if (t < 1.0 / 6)
		{
			return p + (q - p) * 6 * t;
		}
		if (t < 0.5)
		{
			return q;
		}
		if (t < 2.0 / 3)
		{
			return p + (q - p) * (2.0 / 3 - t) * 6;
		}
		return p;
	}

	private static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}
}
