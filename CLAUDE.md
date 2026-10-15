# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

@AGENTS.md

---

# POH Forest design notes

## Limits (looked up, not measured in-game)
- The POH is an instance: the loaded scene is 104x104 tiles (13x13 chunks of 8x8), so no object can sit further out than that. `EXTENDED_SCENE_SIZE` (184) only applies to non-instanced scenes.
- GPU plugin: draw distance max 184 tiles (default 25), fog depth max 100. 117HD: same maxima, default draw distance 50. Both read from the 1.13.2 sources. The user's draw-distance setting, not the plugin, decides how much of the forest is actually drawn.
- The plot of rooms is 8x8 rooms in the middle of the scene; the ground exists only under it. Trees outside the plot stand in the void (no fake ground yet).

## Footprint
`ForestLayout.builtTiles`: a chunk is "built" if any tile on any plane has a wall, decorative or game object. Plain grass filler chunks have none, so forest grows over them. Recomputed on every scene load, so build mode and room changes are picked up (LOADING despawns, the next tick respawns). Debug log prints the chunk map (`#` built, `.` filler).

## Placement
Distance field (Chebyshev) from built tiles; nothing within `clearingMargin`. Jittered candidate per tile, accepted nearest-first by Poisson-disc spacing that grows from `nearSpacing` to `farSpacing` over `falloffTiles`; scale grows to `outerBoostPct`. Budget cuts the outer band first. Seeded per tile + preset, so deterministic. Undergrowth is a second pass that avoids trees.

## Presets
`ForestPreset` enum, each a `ForestStyle` (weighted `Species` lists, spacing, scale range, optional `Recolour`, undergrowth). `CUSTOM` takes a palette and the slider values. All model IDs live in `ForestModels` with the source object ID. Only willow (8131) is verified in-game.
- Assumption, unverified: textured foliage does not respond to a hue remap, so Autumn uses only untextured tree models (cache check: 4079, 4082, 1630, 1701 have no textures).
- Recolour works on Jagex 16-bit HSL: hue = bits 10-15.

## Testing
Config changes respawn immediately. Hotkey (default Ctrl+Shift+F) cycles presets. Debug overlay shows counts, spawn time and the spacing curve.

## Fake ground
Void tiles have no `Tile`, so the scene cannot be painted there. Instead model 7768 (a flat 384x384 quad, colour 0) is recoloured to the sampled grass HSL, mirrored (negative x scale, so the normal faces up) and scaled to 1026 units: one quad per template chunk that is -1. Ground and trees are lifted to the plot's tile height (void tiles read height 0). Assumptions not yet confirmed in-game: the quad is visible from above, its colour matches after lighting (use the `groundLightness` setting), and the plot is flat.
