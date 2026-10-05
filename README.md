# Material Nexus

**Unify materials. Understand recipes. Control the pack.**

Material Nexus is a NeoForge 1.21.1 mod for modpack authors. It discovers material forms across a pack, lets the author choose canonical providers, manages tags and recipe families, proposes missing recipes, and exposes the result through an in-game management UI.

> Status: architecture/bootstrap dossier. The runtime implementation is intentionally incremental; the contracts below are the source of truth for the first implementation pass.

## Target

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- Server-authoritative, data-driven
- No KubeJS dependency
- JEI/EMI as optional presentation integrations
- Create/Mekanism/Immersive Engineering/Thermal through optional adapters

## First command

`/materials`

## Core promise

A pack author should be able to open a material, for example Copper, see every detected form and provider, choose the canonical item for each form, inspect every recipe family, approve or reject changes, and apply the resulting policy without manually editing dozens of JSON files.

## Design rules

1. Discover first, mutate second.
2. Preview before destructive changes.
3. Never silently invent balance.
4. Build an immutable resolved snapshot on reload.
5. Keep hot paths free of analysis and allocations.
6. Optional integrations are isolated adapters.
7. Client code never leaks into common/server packages.
8. Every automatic decision has an explanation.

## Build

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
./gradlew runServer
```

## Documentation

Start with `docs/00-PROJECT.md`, then `docs/01-ARCHITECTURE.md`, `docs/02-DOMAIN-MODEL.md`, `docs/05-RECIPE-SYSTEM.md`, `docs/15-CONVENTIONS.md` and `docs/16-DECISIONS.md`.
