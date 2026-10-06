# 13 — Roadmap

V1 delivers a usable GUI from the start (decision: grilling session 2026-10-05). Scope is cut per screen, not deferred to a late phase.

## Phase 0 — Foundation

- [x] domain contract
- [x] architecture contract
- [x] Gradle/NeoForge 1.21.1 bootstrap
- [x] main mod entry point
- [x] `/materials` command + creative "Nexus Terminal" item (permission 2, server-checked; placeholder screen)
- [x] baseline tests (JUnit + GameTest)

## Phase 1 — Discovery and read-only GUI

- [x] item/block discovery (via item tags; block items carry mirrored tags)
- [x] common material tags (`c:<folder>/<material>`)
- [ ] explicit material definitions
- [ ] confidence/evidence model
- [ ] read-only snapshot index
- [x] targeted network queries (list page + material detail, permission re-checked per request)
- [x] material list screen (search + pagination)
- [x] material detail screen (forms, providers, `Why?`; read-only until MNX-022)
- [ ] read-only diagnostics on dedicated servers

## Phase 2 — Unification and Apply

- [x] canonical provider resolution (pure resolver; policy loading comes with MNX-022)
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
