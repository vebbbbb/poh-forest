package com.pohforest;

/**
 * Every model ID the plugin uses. All IDs were read from the object definitions in the live game
 * cache (the object ID is in each comment). Only WILLOW has been seen rendering in-game so far;
 * the rest are cache-verified but still need an in-game check (cycle the presets and look).
 *
 * Recolour arrays are the object definition's own find/replace pairs (HSL shorts).
 */
final class ForestModels
{
	private ForestModels()
	{
	}

	// ---- Pine / evergreen: Pine tree objects 42377..42383 (2x2, scale 128,112,128) ----
	// Object 42377: trunk 42498 + foliage 42501 + needles 42517, with the object's recolour
	static final Species PINE_A = Species.of("Pine A", 3, 1.0, 42498, 42501, 42517)
		.recolored(new int[]{16685, 16681, 16677, 16673, 16668, 16664}, new int[]{16540, 16536, 16532, 16656, 16652, 16904});
	// Object 42383: trunk + needles, no recolour
	static final Species PINE_B = Species.of("Pine B", 2, 1.0, 42507, 42517);
	// Object 42378: shorter variant
	static final Species PINE_C = Species.of("Pine C", 2, 1.0, 42508, 42501, 42515)
		.recolored(new int[]{16685, 16681, 16677, 16673, 16668, 16664}, new int[]{16540, 16536, 16532, 16656, 16652, 16904});

	// ---- Deciduous ----
	// Tree, object 1276 (2x2); bark recolour 3470 -> 5665
	static final Species TREE_A = Species.of("Tree A", 3, 1.0, 1570).recolored(new int[]{3470}, new int[]{5665});
	// Tree, object 1278 (2x2)
	static final Species TREE_B = Species.of("Tree B", 3, 1.0, 1637).recolored(new int[]{3470}, new int[]{5665});
	// Oak tree, object 9734 (3x3)
	static final Species OAK = Species.of("Oak", 2, 1.5, 1571);
	// Willow tree, object 10819 (2x2): the only model verified in-game
	static final Species WILLOW = Species.of("Willow", 2, 1.0, 8131);
	// Maple tree, object 4674 (2x2)
	static final Species MAPLE = Species.of("Maple", 2, 1.0, 8023);
	// Trees with untextured foliage, so a hue remap works on them (autumn preset)
	// Tree, object 4052 (2x2)
	static final Species TREE_FLAT_A = Species.of("Tree flat A", 3, 1.0, 4079);
	// Tree, object 4050 (3x3)
	static final Species TREE_FLAT_B = Species.of("Tree flat B", 2, 1.5, 4082);
	// Tree, object 6578 (2x2); recolour 6583 -> 6565, 15799 -> 16039
	static final Species TREE_FLAT_C = Species.of("Tree flat C", 2, 1.0, 1630)
		.recolored(new int[]{6583, 15799}, new int[]{6565, 16039});
	// Tree, object 1301 (2x2); placeholder greys 61/49/41 -> foliage colours
	static final Species TREE_FLAT_D = Species.of("Tree flat D", 2, 1.0, 1701)
		.recolored(new int[]{61, 49, 41}, new int[]{16916, 13840, 4626});

	// ---- Magic / yew ----
	// Magic tree, object 4537 (2x2)
	static final Species MAGIC_A = Species.of("Magic A", 3, 1.0, 12926);
	// Yew tree, object 4536 (2x2)
	static final Species YEW_A = Species.of("Yew A", 3, 1.0, 12949);
	// Yew tree, object 10822 (2x2 in the object, canopy is wide)
	static final Species YEW_B = Species.of("Yew B", 2, 1.5, 8157);
	// Magic tree variants, objects 8398 / 8399 (3x3); 7768 is a 2-face base quad
	static final Species MAGIC_B = Species.of("Magic B", 2, 1.5, 7993, 7768);
	static final Species MAGIC_C = Species.of("Magic C", 2, 1.5, 7995, 7768);

	// ---- Dead / spooky ----
	// Dead tree, objects 1282 / 1290 / 1333 (2x2)
	static final Species DEAD_A = Species.of("Dead A", 3, 1.0, 1715);
	static final Species DEAD_B = Species.of("Dead B", 2, 1.0, 1718);
	static final Species DEAD_C = Species.of("Dead C", 3, 1.0, 1607);
	// Dead Yew object 8525, Dead Maple object 8454, Dead Magic Tree object 8423 (all 3x3)
	static final Species DEAD_YEW = Species.of("Dead yew", 2, 1.5, 8146, 7768);
	static final Species DEAD_MAPLE = Species.of("Dead maple", 2, 1.5, 8014, 7768);
	static final Species DEAD_MAGIC = Species.of("Dead magic", 2, 1.5, 7992, 7768);

	// ---- Jungle ----
	// Jungle tree, objects 15948 / 15950 / 15951 (2x2)
	static final Species JUNGLE_A = Species.of("Jungle A", 3, 1.0, 15808);
	static final Species JUNGLE_B = Species.of("Jungle B", 2, 1.0, 15809);
	static final Species JUNGLE_C = Species.of("Jungle C", 3, 1.0, 15810);
	// Palm tree, objects 2577 (2x2) / 2237 (3x2)
	static final Species PALM_A = Species.of("Palm A", 3, 1.0, 1612);
	static final Species PALM_B = Species.of("Palm B", 2, 1.0, 1593);
	// Tropical palm, objects 57815 / 57816 (1x1)
	static final Species TROPICAL_A = Species.of("Tropical A", 2, 0.5, 58690);
	static final Species TROPICAL_B = Species.of("Tropical B", 2, 0.5, 58691);

	// ---- Undergrowth ----
	// Bush, objects 1118 / 1119 / 1120 (2x2)
	static final Species BUSH_A = Species.of("Bush A", 3, 0.8, 1669);
	static final Species BUSH_B = Species.of("Bush B", 3, 0.8, 1670);
	static final Species BUSH_C = Species.of("Bush C", 2, 0.8, 1671);
	// Fern, objects 1298 / 1299 / 1300 (1x1)
	static final Species FERN_A = Species.of("Fern A", 3, 0.4, 1707);
	static final Species FERN_B = Species.of("Fern B", 3, 0.4, 1653);
	static final Species FERN_C = Species.of("Fern C", 2, 0.4, 1655);
	// Rocks, object 154 (1x1)
	static final Species ROCKS = Species.of("Rocks", 2, 0.5, 32530);
	// Tree stump, object 1354 (2x2)
	static final Species STUMP = Species.of("Stump", 1, 0.8, 1635);
	// Jungle plant, objects 1399 (2x2) / 1204 (1x1)
	static final Species JUNGLE_PLANT_A = Species.of("Jungle plant A", 3, 0.8, 1677);
	static final Species JUNGLE_PLANT_B = Species.of("Jungle plant B", 3, 0.5, 1679);
}
