# 14 — Backlog

| ID | Priority | Ticket | Acceptance |
|---|---|---|---|
| MNX-001 | P0 | Mod bootstrap | Client/server launch with no optional mods |
| MNX-002 | P0 | Material IDs | Stable immutable IDs and codecs |
| MNX-003 | P0 | Registry discovery | Discover items/blocks deterministically |
| MNX-004 | P0 | Canonical resolver | Same inputs always yield same result |
| MNX-005 | P0 | Snapshot reload | Failed analysis shows diagnostics and generates nothing |
| MNX-006 | P0 | `/materials` + Nexus Terminal | OP level 2 opens the GUI; server re-checks permission; dedicated server is read-only |
| MNX-012 | P0 | Network | Targeted paginated GUI data, server-validated |
| MNX-021 | P0 | Material screens | List + detail with providers, canonical choice and `Why?` |
| MNX-022 | P0 | Generated datapack | Policy in `config/` → global pack with manifest, injected in every world |
| MNX-011 | P0 | Preview / Apply | All mutations shown before apply; Apply regenerates and reloads |
| MNX-023 | P1 | Revert last apply | Restores previous policy, regenerates, reloads |
| MNX-024 | P1 | Pre-MNX analysis | Overridden files analyzed from the resource stack |
| MNX-007 | P1 | Tags | Previewable tag changes via NeoForge `remove` entries |
| MNX-008 | P1 | Recipe normalization | Vanilla recipe types supported |
| MNX-009 | P1 | Recipe families | Plate/ingot/etc. variants grouped and shown |
| MNX-025 | P1 | Output rewriting | Vanilla types only; off by default; previewed |
| MNX-026 | P1 | Almost Unified arbitration | Per-domain ownership; overlaps reported |
| MNX-027 | P0 | False duplicates | Rock variants, same-mod variants, umbrella tags and exclusions are listed but never unified (ADR-014) |
| MNX-010 | P1 | Recipe proposals | Missing families produce reviewable proposals |
| MNX-013 | P2 | JEI | Disabled/generated recipes represented correctly |
| MNX-014 | P2 | EMI | Same behavior through separate adapter |
| MNX-015 | P2 | Create | Sheets/pressing/rolling semantics recognized |
| MNX-016 | P2 | Mekanism | Dust/ingot/plate conventions recognized |
| MNX-017 | P2 | IE | Plate/wire/rod conventions recognized |
| MNX-018 | P2 | Thermal | Plate/gear/etc. recognized |
| MNX-019 | P3 | Presets | Five initial presets |
| MNX-028 | P1 | In-world conversion | Applied alternatives become canonical when dropped, on login and when a vanilla container opens (ADR-015) |
| MNX-030 | P1 | Recipe inputs | Literal alternative inputs of vanilla recipes become the canonical item; collapsed duplicates are disabled |
| MNX-035 | P2 | Form views | Cross-material views per form (all ingots, all rods...) to decide form by form; same pending/preview flow |
| MNX-036 | P2 | Process rules | "All rods are made in machine A (ratio x), machine B (ratio y)": per-form, per-machine generation rules written through recipe formats; balance-affecting, so explicit policy + Preview only (ADR-003) |
| MNX-020 | P3 | KubeJS bridge | Optional scripting API, no dependency |
