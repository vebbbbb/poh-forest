# POH Forest — RuneLite plugin

A RuneLite Plugin Hub plugin that surrounds the player-owned house (POH) with a client-side forest so the house doesn't sit in a black void. Purely cosmetic: only the local player sees it, and it never changes game state.

## Stack
- Java 11, Gradle. Based on the `runelite/example-plugin` template.
- RuneLite client API (`net.runelite.api`, `net.runelite.client`). Use the API version the template pins. Don't bump it unless the build requires it.
- Run locally with the template's test launcher (`src/test/java/.../PohForestPluginTest.java` → `ExternalPluginManager.loadBuiltin(...)`), or `./gradlew run` if it is configured.

## Core approach
- Detect when the player is inside their POH: instanced region plus the house's region/template chunks. Check how the core `PohPlugin` and community plugins detect it, and don't guess region IDs.
- Spawn trees as `RuneLiteObject`s: `client.createRuneLiteObject()`, `setModel(client.loadModel(id))`, `setLocation(LocalPoint, plane)`, `setActive(true)`.
- Place trees in a ring/band around the house footprint, on tiles that have no house content. Give them a seeded random spread, rotation and model choice so the layout stays stable between visits.
- Despawn everything on leaving the POH, on `GameStateChanged` (LOADING/LOGIN_SCREEN, hopping), and in `shutDown()`. Never leak objects.
- Re-spawn after every scene load, because the POH scene rebuilds when entering, in build mode, and on room changes.

## Known unknowns (investigate, don't assume)
- **Terrain outside the house.** Tiles outside the house may have no ground and zero height. Find out how `RuneLiteObject`s render there and whether we also need a fake ground (a flat grass model or tile overlay).
- **Tree model IDs.** Get them from the game cache (RuneLite cache tools or an OSRS cache viewer) and verify each one in-game before committing it. Don't invent IDs.
- **Scene bounds.** The scene is 104×104 tiles, and objects must stay inside it.
- **Performance.** Keep the object count configurable, with a sane default (e.g. ≤150). Test with GPU and 117 HD.

## Config (`PohForestConfig`)
Start minimal: enabled toggle, density, radius/band width, tree types (multi-select or toggles), seed. Add more only when it's actually needed.

## Rules
- Must comply with Jagex's third-party client guidelines: visual only, no automation, no input injection, no menu changes.
- Plugin Hub requirements: `runelite-plugin.properties` filled in (displayName, author, description, tags, plugins), a LICENSE (keep the template's BSD-2), a README.md with a screenshot, and no extra dependencies unless they're essential.
- Keep the code small and readable. One plugin class, one config, plus a helper class for placement/spawning if it grows.
- Commit in small, working steps with clear messages.

## Workflow
1. Make it build and load unchanged.
2. Detect POH entry/exit and log it.
3. Spawn one hardcoded tree next to the player and confirm it renders.
4. Ring placement + despawn lifecycle.
5. Config options.
6. Polish: model variety, fake ground if needed, README + screenshots.
7. Plugin Hub submission: PR to `runelite/plugin-hub` adding `plugins/poh-forest` with `repository=` and `commit=`.
