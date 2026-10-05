# 01 — Architecture

## Vue d'ensemble

```text
NeoForge lifecycle / reload
        │
        ▼
 Discovery ──► Material model ──► Recipe analysis
        │              │                 │
        └──────────────┴───────► Policy engine
                                      │
                                      ▼
                              Resolved Snapshot
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
              Server gameplay                    Client query/UI
                    │                                   │
                    └──────────── network ──────────────┘
```

## Modules

- `core/domain`: IDs, materials, forms, providers, recipe families.
- `core/discovery`: observe registries/tags/recipes and produce neutral facts.
- `core/policy`: pack-author intent and precedence rules.
- `core/resolution`: deterministic selection of canonical resources.
- `core/recipe`: classification, duplicate detection and generation proposals.
- `registry`: Material Nexus own codecs/registries where needed.
- `integration`: Create, Mekanism, Immersive Engineering, Thermal, JEI, EMI.
- `command`: `/materials`, `/materials reload`, diagnostics.
- `client`: screens, widgets, network requests.
- `gametest`: acceptance tests.

## Applying changes: generated datapack

Material Nexus never mutates recipes or tags in memory. Apply turns the policy (`config/materialnexus/policies/`) into a global generated datapack (`config/materialnexus/generated/`) and triggers `/reload`; vanilla applies it atomically (ADR-007, ADR-008).

## Read-only snapshot

A `ResolvedSnapshot` is an immutable index of the analysis (materials, providers, classifications, explanations) used by the GUI and diagnostics. It is rebuilt after each reload and swapped by reference. A failed analysis shows diagnostics and generates nothing (ADR-002).

## No tick analysis

The hot path must never walk every recipe, tag or material. All expensive work belongs to discovery/reload/explicit analysis.
