# Material Nexus — operational contract

Mod Minecraft **NeoForge 1.21.1**, Java 21. Toolchain and documentation conventions intentionally follow the recent DrimoZ NeoForge projects.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew runGameTestServer
./gradlew runData
./gradlew runClient
./gradlew runServer
```

## Non-negotiable rules

- Common/server code never imports `net.minecraft.client`.
- Discovery and recipe analysis happen at controlled lifecycle points, never every tick.
- No persistent inventory scanning, item history, telemetry or world-wide container indexing. Applied unification converts alternatives only when the game touches them (item entering the world, player login, vanilla container opened), never by walking the world (ADR-015).
- Nothing is unified at load: only a player decision (GUI choice or policy file) followed by Apply changes the pack.
- Registry/data mutations must be deterministic and explainable.
- Destructive recipe removal and balance-affecting generation require an explicit policy decision.
- Missing recipes are proposals, not automatic balance changes.
- Every user-visible string exists in `en_us.json` and `fr_fr.json`.
- Every optional integration is isolated behind a compat adapter and safe when the mod is absent.
- A reload builds a new immutable resolved snapshot and swaps it atomically.
- Network packets are validated server-side; no periodic S→C state stream.

## Tests

JUnit: pure policy, matching, recipe classification, resolution and codecs.
GameTest: registry/data lifecycle, reload, tags, recipes and integration behavior that needs a Minecraft runtime.

## Git

One branch per ticket. Commit messages use the ticket prefix: `MNX-012: ...`. No WIP commits on the main branch.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
