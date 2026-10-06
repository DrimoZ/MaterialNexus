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
- [x] policy files in `config/materialnexus/policies/` (read on every reload)
- [x] generated global datapack + `manifest.json` (writer + injection; content arrives with tag/recipe actions)
- [x] tag policy (NeoForge `remove` entries for unified alternatives; optional conversion recipes)
- [x] preview screen → Apply → `/reload` (canonical choices; backup in policies.bak)
- [x] revert last apply (swap with policies.bak; a second revert redoes)
- [ ] pre-MNX analysis via resource stack
- [ ] Almost Unified detection and per-domain arbitration

## Phase 3 — Recipe intelligence

- [x] vanilla recipe adapters (result rewrite for the 8 vanilla types)
- [ ] recipe families screen
- [ ] duplicate classification
- [x] recipe disabling (exact duplicates after rewrite; modded types untouched)
- [x] output rewriting (vanilla types; pre-MNX JSON from the resource stack)
- [ ] missing recipe proposals

## Phase 4 — Integrations

- [ ] JEI
- [ ] EMI
- [x] Create (processing recipe outputs and inputs; sequenced assembly unsupported)
- [x] Mekanism (item outputs and inputs of machine recipes; chemicals untouched)
- [x] Immersive Engineering (machine recipes incl. nested secondaries)
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
