# 13 — Roadmap

V1 delivers a usable GUI from the start (decision: grilling session 2026-10-05). Scope is cut per screen, not deferred to a late phase.

## Phase 0 — Foundation

- [x] domain contract
- [x] architecture contract
- [x] Gradle/NeoForge 1.21.1 bootstrap
- [x] main mod entry point
- [ ] `/materials` command + creative "Nexus Terminal" item (permission 2, server-checked)
- [x] baseline tests (JUnit + GameTest)

## Phase 1 — Discovery and read-only GUI

- [x] item/block discovery (via item tags; block items carry mirrored tags)
- [x] common material tags (`c:<folder>/<material>`)
- [ ] explicit material definitions
- [ ] confidence/evidence model
- [ ] read-only snapshot index
- [ ] targeted network queries
- [ ] material list screen
- [ ] material detail screen (forms, providers, `Why?`)
- [ ] read-only diagnostics on dedicated servers

## Phase 2 — Unification and Apply

- [ ] canonical provider resolution
- [ ] policy files in `config/materialnexus/policies/`
- [ ] generated global datapack + `manifest.json`
- [ ] tag policy (NeoForge `remove` entries)
- [ ] preview screen → Apply → `/reload`
- [ ] revert last apply
- [ ] pre-MNX analysis via resource stack
- [ ] Almost Unified detection and per-domain arbitration

## Phase 3 — Recipe intelligence

- [ ] vanilla recipe adapters
- [ ] recipe families screen
- [ ] duplicate classification
- [ ] recipe disabling (any type)
- [ ] output rewriting (vanilla types)
- [ ] missing recipe proposals

## Phase 4 — Integrations

- [ ] JEI
- [ ] EMI
- [ ] Create
- [ ] Mekanism
- [ ] Immersive Engineering
- [ ] Thermal

## Phase 5 — V2 / pack author polish

- [ ] recipe editor
- [ ] dashboard with counts
- [ ] Loot and JEI/EMI tabs
- [ ] presets
- [ ] import/export policy
- [ ] diagnostics report
- [ ] full apply history (only if a real need appears)
- [ ] KubeJS optional API
- [ ] documentation/wiki
