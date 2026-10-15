package com.pohforest;

/** One placeable object: its model parts, the object-definition recolours, and its footprint. */
final class Species
{
	final String name;
	final int[] models;
	final short[] find;
	final short[] replace;
	final int weight;
	/** Rough footprint radius in tiles, used to keep trees off each other. */
	final double radius;

	private Species(String name, int weight, double radius, int[] models, short[] find, short[] replace)
	{
		this.name = name;
		this.weight = weight;
		this.radius = radius;
		this.models = models;
		this.find = find;
		this.replace = replace;
	}

	static Species of(String name, int weight, double radius, int... models)
	{
		return new Species(name, weight, radius, models, new short[0], new short[0]);
	}

	Species recolored(int[] find, int[] replace)
	{
		short[] f = new short[find.length];
		short[] r = new short[replace.length];
		for (int i = 0; i < find.length; i++)
		{
			f[i] = (short) find[i];
			r[i] = (short) replace[i];
		}
		return new Species(name, weight, radius, models, f, r);
	}
}
