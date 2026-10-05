# 15 — Conventions

The following intentionally inherit the useful parts of the recent DrimoZ mods:

- English design docs and in-game identifiers, concise README;
- clear `client/`, common and `gametest/` boundaries;
- `build`, `runGameTestServer`, `runClient`, `runServer`, `runData` as standard commands;
- generated resources committed when datagen is introduced;
- optional dependencies isolated and tested both present and absent;
- GameTests for world/runtime behavior, JUnit for pure logic;
- modpack permissions documented explicitly;
- MIT code / separate asset licence;
- no hidden behavior in the hot path;
- every important architecture decision documented, including rejected approaches.

Material Nexus differs where its product requires it: the core is domain/policy/resolution oriented instead of block-centric.
