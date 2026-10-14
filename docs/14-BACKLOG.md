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
| MNX-037 | P1 | Process templates | Data templates for machines with no example or ratios a copy cannot carry; every written recipe decoded by the game first (ADR-018) |
| MNX-038 | P2 | Larger dev pack | Addons and metal-heavy mods in dev runs |
| MNX-039 | P1 | Created items | Items for missing forms from items.json, tinted templates, tagged, restart to register (ADR-019) |
| MNX-040 | P1 | More forms | Tiny/dirty dusts, clumps, shards, crystals, sheetmetal; untagged forms through declared name patterns; coal/charcoal as gems; no ingot or wire offered for gem materials |
| MNX-041 | P1 | Forms as data | Tag folders and name patterns in material_nexus/forms; untagged-form audit in /materials report |
| MNX-043 | P1 | Addon recipe formats | Create Crafts & Additions, Create Metallurgy, Create New Age, Ex Deorum, Oritech, Occultism, Immersive Petroleum, Silent Gear, Extreme Reactors; Process tab warns about undecided duplicates |
| MNX-044 | P2 | Preview performance | Timing GameTest on the dev pack (worst case < 2 s, fails above 5 s) |
| MNX-045 | P2 | Relations as data | Missing-recipe relations added/removed in material_nexus/forms |
| MNX-020 | P3 | KubeJS bridge | Optional scripting API, no dependency |
| MNX-076 | P1 | Decisions found in scripts | Tag edits in memory and KubeJS recipe edits read at load; the item scripts keep per form proposed as a choice; Preview warns on opposite choices (docs/20, ADR-022) |
| MNX-077 | P1 | Where scripts decide | Each decision placed on the script lines naming its set-aside items ("seen in unify.js:12"), by text search; nothing shown for ids built in code (docs/20) |
| MNX-078 | P1 | Missing tags | `add_missing_tags`: items known by name pattern or alias get their convention tag (and folder tag) on Apply, where the pack uses it (ADR-023) |
| MNX-079 | P1 | Tag edits | `tag_edits` per material/form: Shift + right click takes an item out of its form tag, Data view adds one by id; discovery and resolution follow (ADR-024) |
| MNX-080 | P2 | Tag diagnostics | `/materials report` "Tags": tags items lack (what Add missing tags writes), recipes asking for an empty material tag, recipes asking for `c:` tags |
| MNX-081 | P2 | Forms removable as data | `remove_folders` in material_nexus/forms: a folder (built-in or declared) is no longer read |
| MNX-086 | P0 | Guesses the player can overrule | An item is "named after" a material only when tagged as it; "It is the same item" makes an item left alone on a guess a candidate again |
| MNX-087 | P0 | Store and wiki entry points | How to open the screen and the links at the top of the store page; wiki home by need |
| MNX-088 | P1 | Recipes drawn by the viewer | JEI or EMI draws the recipe in the inspector and next to the pointer; no renderer of our own; safe with no viewer |
| MNX-089 | P1 | Screen legibility | Covered by the UI redesign, MNX-093 to MNX-104 (docs/22) |
| MNX-090 | P1 | Compat by evidence | `/materials report` ranks unhandled recipe types by recipes naming a material item; Thermal with the 1.20.1 port |
| MNX-091 | P2 | Texture by file name | `config/materialnexus/textures/<material>_<form>.png` is used for a created item, nothing declared |
| MNX-092 | P2 | Tag folder as a form | Declared from Rule files, written in the declared patterns file, applied like any change |
| MNX-093..104 | P1 | UI redesign | Theme roles, shell, tables with inspector, review panel, settings; captures and wiki regenerated (docs/22, docs/07) |
| MNX-107 | P1 | New default item templates | 25 redrawn templates with stretched levels, double plate as a form, the drawings as an optional "soft" resource pack; sheet template shipped, offered once its relation to plate is a setting |
| MNX-109 | P1 | Generated pack safe without its mods | Every recipe the pack writes carries an item_exists condition per item it names: a pack that drops a mod after Apply still opens its worlds (on 1.20.1 one cooking recipe with an unknown result stopped the whole datapack load) |
| MNX-111 | P1 | Preview performance on large packs | Measure where the full Preview spends its time on the 1.20.1 dev pack (24.5k recipes, 7.5 s against the 5 s of MNX-044: rewrites 3.3 s, reading recipes 2.2 s, processes 2 s) and bring it back under 5 s, or justify a new threshold |
| MNX-112 | P2 | Dead code cleanup | Unused types, constructor and method removed; single-implementation `Drawer` interface folded into `ReviewDrawer`; compatibility doc describes the integrations as they are |
| MNX-113 | P1 | Process rules at scale | Recipes indexed once by what they make: each target reads only its own producers and examples (no scan of every recipe per form); same files and effects, in the same order |
