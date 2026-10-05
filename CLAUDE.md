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
- No persistent inventory scanning, item history, telemetry or world-wide container indexing.
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
