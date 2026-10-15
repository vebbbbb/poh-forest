package com.pohforest;

import java.util.Arrays;
import java.util.List;

/**
 * Every model ID the plugin uses. All IDs were read from object definitions in the live game
 * cache (the object ID is in each comment). The pines were judged good in-game; the redwood and
 * the ground cover are new and still need an in-game look.
 *
 * Recolour arrays are the object definition's own find/replace pairs (HSL shorts).
 */
final class ForestModels
{
	private ForestModels()
	{
	}

	private static final int[] PINE_FIND = {16685, 16681, 16677, 16673, 16668, 16664};
	private static final int[] PINE_REPL = {16540, 16536, 16532, 16656, 16652, 16904};

	// ---- Pine trees: objects 42377..42383, 57162, 57163, 60092 (2x2, ~7 tiles tall) ----
	// Object 42377: trunk 42498 + foliage 42501 + needles 42517
	static final Species PINE_A = Species.of("Pine A", 3, 1.0, 42498, 42501, 42517).recolored(PINE_FIND, PINE_REPL);
	// Object 42383: trunk 42507 + needles 42517, no recolour
	static final Species PINE_B = Species.of("Pine B", 2, 1.0, 42507, 42517);
	// Object 42378: shorter variant
	static final Species PINE_C = Species.of("Pine C", 2, 1.0, 42508, 42501, 42515).recolored(PINE_FIND, PINE_REPL);
	// Object 42379
	static final Species PINE_D = Species.of("Pine D", 2, 1.0, 42508, 42499, 42517).recolored(PINE_FIND, PINE_REPL);
	// Object 42381
	static final Species PINE_E = Species.of("Pine E", 2, 1.0, 42498, 42500, 42517).recolored(PINE_FIND, PINE_REPL);
	// Object 60092: no needle ring
	static final Species PINE_F = Species.of("Pine F", 2, 1.0, 42498, 42501).recolored(PINE_FIND, PINE_REPL);

	// ---- Redwood: full-tree models of the Varlamore redwood objects (hue 2 trunk, hue 10 canopy) ----
	// Redwood tree, object 34209 / 34205 family: model 36721 (730 tall, 2976 faces)
	static final Species REDWOOD_A = Species.of("Redwood A", 2, 1.3, 36721);
	// Redwood tree, object 34209: model 36724 (546 tall, 2976 faces)
	static final Species REDWOOD_B = Species.of("Redwood B", 2, 1.3, 36724);

	// ---- Undergrowth ----
	// Pine saplings, objects 42388 / 42389 (1x1)
	static final Species SAPLING_A = Species.of("Sapling A", 3, 0.5, 42497);
	static final Species SAPLING_B = Species.of("Sapling B", 3, 0.5, 42503);
	// Bush, objects 1118 / 1119 (2x2)
	static final Species BUSH_A = Species.of("Bush A", 2, 0.8, 1669);
	static final Species BUSH_B = Species.of("Bush B", 2, 0.8, 1670);
	// Fern, objects 1298 / 1299 / 1300 (1x1)
	static final Species FERN_A = Species.of("Fern A", 3, 0.4, 1707);
	static final Species FERN_B = Species.of("Fern B", 3, 0.4, 1653);
	// Ferns for the redwood floor: objects 5128 (2x2), 22804 / 22805 (1x1), 54935 (2x2)
	static final Species FERN_C = Species.of("Fern C", 3, 0.8, 12932);
	static final Species FERN_D = Species.of("Fern D", 3, 0.4, 23477);
	static final Species FERN_E = Species.of("Fern E", 3, 0.4, 23478);
	static final Species FERN_F = Species.of("Fern F", 2, 0.8, 54484);
	// Mushrooms: objects 6705 / 6706 (1x1), 14443 (2x2), 14444 (1x1)
	static final Species MUSHROOM_A = Species.of("Mushroom A", 2, 0.4, 11984);
	static final Species MUSHROOM_B = Species.of("Mushroom B", 2, 0.4, 11985);
	static final Species MUSHROOM_C = Species.of("Mushroom C", 1, 0.8, 14686);
	static final Species MUSHROOM_D = Species.of("Mushroom D", 2, 0.5, 14639);
	// Rocks, object 154 (1x1)
	static final Species ROCKS = Species.of("Rocks", 2, 0.5, 32530);
	// Tree stump, object 1354 (2x2)
	static final Species STUMP = Species.of("Stump", 1, 0.8, 1635);

	// ---- Ground cover (grass tufts scattered over the fake ground) ----
	// Long grass, object 4815 (1x1)
	static final Species GRASS_LONG = Species.of("Long grass", 3, 0.3, 4730);
	// Grass, objects 3795 / 14003 (1x1)
	static final Species GRASS_A = Species.of("Grass A", 3, 0.3, 3795);
	static final Species GRASS_B = Species.of("Grass B", 3, 0.3, 3951);
	// Grass, object 3794 (1x1), recolour 13212 -> 13204
	static final Species GRASS_C = Species.of("Grass C", 2, 0.3, 3794).recolored(new int[]{13212}, new int[]{13204});

	static final List<Species> COVER = Arrays.asList(GRASS_LONG, GRASS_A, GRASS_B, GRASS_C);
}
