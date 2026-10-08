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
- [x] explicit material definitions (aliases merge spellings; shipped: aluminium -> aluminum)
- [x] confidence/evidence model
- [x] read-only snapshot index
- [x] targeted network queries (list page + material detail, permission re-checked per request)
- [x] material list screen (search + pagination)
- [x] material detail screen (forms, providers, `Why?`; read-only until MNX-022)
- [x] read-only diagnostics on dedicated servers (GUI read-only, report from the console)

## Phase 2 — Unification and Apply

- [x] canonical provider resolution (pure resolver; policy loading comes with MNX-022)
- [x] policy files in `config/materialnexus/policies/` (read on every reload)
- [x] generated global datapack + `manifest.json` (writer + injection; content arrives with tag/recipe actions)
- [x] tag policy (NeoForge `remove` entries for unified alternatives; optional conversion recipes)
- [x] preview screen → Apply → `/reload` (canonical choices; backup in policies.bak)
- [x] revert last apply (swap with policies.bak; a second revert redoes)
- [x] pre-MNX analysis via resource stack (tags via manifest, recipes via resource stack)
- [x] Almost Unified detection and per-domain arbitration (unarbitrated domains left to AU)

## Phase 3 — Recipe intelligence

- [x] vanilla recipe adapters (result rewrite for the 8 vanilla types)
- [x] recipe families screen (Recipes tab: every recipe producing a form, with its status)
- [x] duplicate classification (exact duplicates after rewrite)
- [x] recipe disabling (exact duplicates after rewrite; modded types untouched)
- [x] output rewriting (vanilla types; pre-MNX JSON from the resource stack)
- [x] missing recipe proposals (standard form conversions with no recipe; shown, never created)

## Phase 4 — Integrations

- [x] JEI (applied alternatives hidden; shown again on revert)
- [x] EMI (applied alternatives hidden; `-Pemi` to run it in dev)
- [x] Create (processing recipe outputs and inputs; sequenced assembly unsupported)
- [x] Mekanism (item outputs and inputs of machine recipes; chemicals untouched)
- [x] Immersive Engineering (machine recipes incl. nested secondaries)
- [ ] Thermal

## Phase 5 — V2 / pack author polish

- [ ] recipe editor
- [ ] dashboard with counts
- [ ] Loot and JEI/EMI tabs
- [x] presets (data-driven, 5 shipped, applied through Preview / Apply / Revert)
- [ ] import/export policy
- [x] diagnostics report (`/materials report` -> config/materialnexus/report.md)
- [ ] full apply history (only if a real need appears)
- [x] KubeJS optional API (read-only bindings, MNX-067)
- [x] decisions found in scripts proposed as choices (MNX-076, docs/20)
- [ ] documentation/wiki
