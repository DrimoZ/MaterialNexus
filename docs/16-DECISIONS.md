# 16 — Architecture decisions

## ADR-001 — No KubeJS dependency

**Decision:** Material Nexus works independently. An optional KubeJS bridge may be added later.

**Why:** the target user should not need a scripting engine to solve material unification.

## ADR-002 — Read-only resolved snapshot

**Decision:** the `ResolvedSnapshot` is an immutable, read-only index (materials, providers, classifications, explanations) rebuilt after each reload and swapped by reference. It is not the mechanism that applies changes: gameplay changes come from the generated datapack (ADR-007), whose atomicity is guaranteed by the vanilla reload.

**Failure:** if analysis fails, MNX shows diagnostics and generates nothing. It does not try to keep an older snapshot alive, since that snapshot would describe recipes/tags that vanilla has already replaced.

**Rejected:** "failed build keeps the old snapshot active" — illusory once vanilla has swapped recipes and tags.

## ADR-003 — Missing recipes are proposals

**Decision:** no automatic balance invention.

**Why:** existence of a material form does not prove that a conversion is desirable or balanced.

## ADR-004 — JEI/EMI are adapters

**Decision:** viewer integrations consume gameplay truth rather than defining it.

**Why:** packs must remain functional without a recipe viewer.

## ADR-005 — No persistent history

**Decision:** no item movement history, telemetry, inventory indexing or server memory. Converting applied alternatives at the moment the game touches an item (ADR-015) is not indexing: nothing is walked, stored or remembered. The single-level policy backup used by "Revert last apply" (ADR-009) is not history. A full apply history may be reconsidered if a real need appears.

**Why:** it adds cost and complexity without serving the core pack-author problem.

## ADR-006 — Explicit precedence

**Decision:** Global < Material < Form < Explicit Resource Override.

**Why:** predictable configuration beats hidden heuristics.

## ADR-007 — Changes are applied through a generated global datapack

**Decision:** Material Nexus never mutates recipes or tags in memory. Apply writes a datapack to `config/materialnexus/generated/`, injected into every world via `AddPackFindersEvent` (position TOP, always enabled), then triggers `/reload`.

- tag changes: tag files with NeoForge `remove` entries / appended values;
- disabled recipes: override of the recipe JSON with `"neoforge:conditions": [{"type": "neoforge:false"}]`;
- rewritten recipes: full recipe JSON at the same ID;
- `manifest.json`: list of every file MNX wrote and why.

**Why:** the vanilla reload is already atomic; the result is inspectable, explainable and survives removal of the mod; no mixin into `TagLoader`/`RecipeManager`. A global folder (not `world/datapacks/`) ships with the modpack `config/` and applies to new worlds.

**Rejected:** in-memory mutation via mixins during reload (fragile, ordering problem between tag loading and recipe analysis, no inspectable output); per-world datapack (missing from new worlds).

## ADR-008 — Policy is the source, the generated pack is build output

**Decision:** `config/materialnexus/policies/` (JSON, editable by hand or by the GUI) is the only source of truth. The generated pack is overwritten on every Apply and contains a `_GENERATED_DO_NOT_EDIT` marker. Hand-made recipe changes belong in the author's own datapack/KubeJS, which MNX then observes as ordinary data.

**Rejected:** editable generated pack merged by MNX (three-way merge, conflicts).

## ADR-009 — Apply flow and single-level revert

**Decision:** Preview → confirm → write policy → regenerate pack → automatic `/reload`. Before writing, the previous policy is copied to a backup; "Revert last apply" restores it, regenerates and reloads.

## ADR-010 — Analysis sees the pre-MNX state

**Decision:** for every file listed in the generated `manifest.json`, analysis reads the original JSON beneath the MNX override via `ResourceManager#getResourceStack`. All other recipes are analyzed as loaded.

**Why:** without the original state, `Why?`, preview and revert are wrong after the first Apply. Cost is proportional to the number of overridden files, at reload only.

## ADR-011 — Output rewriting is explicit and vanilla-only in V1

**Decision:** rewriting a recipe output to the canonical item is a policy action, off by default and visible in preview. V1 supports vanilla recipe types only (crafting, smelting/blasting/smoking/campfire, stonecutting). Mod recipe types are classified but marked `UNSUPPORTED` for rewriting until their adapter exists; they can still be disabled (the `neoforge:false` condition works for any type).

**Rejected:** generic JSON rewriting (searching `"id"` in result fields) — works often, fails silently.

## ADR-012 — Almost Unified arbitration per domain

**Decision:** when Almost Unified is present, the global policy assigns each domain to MNX or AU: `tags`, `output_rewrite`, `recipe_disable`, `viewer_hiding`. MNX disables its own actions in domains owned by AU and reports overlaps in diagnostics. MNX never edits AU's configuration.

**Why:** two unifiers rewriting the same recipes are impossible to debug; AU's config format is not a stable contract.

## ADR-013 — Authoring is a singleplayer/LAN activity

**Decision:** the editing GUI requires permission level 2 and targets singleplayer (cheats on) or LAN. On dedicated servers, OPs get read-only diagnostics. The GUI opens from `/materials` or a creative-only "Nexus Terminal" item; the server re-checks permission on open, so `/give` cannot bypass it.

## ADR-014 - A tag means "usable as", not "the same item"

**Decision:** before unifying a material/form, every provider is sorted out; only interchangeable candidates are unified, the rest are listed with their reason and left untouched.

- ores are split by host rock (`ore`, `deepslate_ore`, `nether_ore`, `end_ore`, from `c:ores_in_ground/*`, else the item name): rock variants never unify with each other, the same rock from two mods does;
- an item also tagged as a more specific material of the same form (fewest members) belongs there: `stick_treated` is a treated_wood rod, not a duplicate of the vanilla stick; umbrella tags like `c:rods/all_metal` end up with nothing to unify;
- several items of one mod in one form are variants (AE2 certus / charged certus), never duplicates;
- `exclude` in `global.json` (`"steel"` or `"steel/rod"`) leaves a material or form alone.

Nothing is ever removed from a tag or a material by these rules: removing anything is the player's decision, which is why an explicit choice may still pick any discovered provider.

**Rejected:** treating every tag member as a duplicate (deepslate vs stone ores, treated vs vanilla sticks); hiding the ore form entirely (cross-mod ores of the same rock are real duplicates).

## ADR-015 - Applied alternatives are converted when the game touches them

**Decision:** once a unification is applied, every alternative item is swapped for the canonical one (count and components kept) at the moments the server already handles that item: an item entity entering a level, a player logging in (inventory, ender chest), a vanilla-backed container being opened (block entity containers, double chests). The table comes from the applied pack manifest, so nothing converts before an apply. One identity lookup per touched stack.

Modded internal storage (machines, AE2 cells, drawers) is not converted: it is not ours to rewrite. The opt-in `conversion_recipes` cover it.

**Rejected:** scanning loaded chunks or all containers (forbidden by ADR-005, costly); converting from the policy files directly (a hand edit would act before any preview).

## ADR-016 - Recipe formats are data, not code

**Decision:** which recipe types Material Nexus can rewrite, and where their item outputs live, is described by JSON files in `data/<namespace>/material_nexus/recipe_formats/` from any datapack: `{"types": [...], "outputs": [...]}`. Material Nexus ships vanilla, Create, Mekanism, Immersive Engineering and Modern Industrialization. A pack author adds another mod, or overrides a shipped file by giving theirs the same id. Files are applied in id order; a later file covering the same type wins.

Everything under an output key is output (stacks with `id` or `item`, lists, nested objects such as IE secondaries); fluid and chemical ids are never items to convert. Inputs need no description: literal `"item"` ingredients outside the output keys are rewritten. Invalid files are skipped with a warning naming them.

**Why:** a pack author must be able to cover the mods of their pack without waiting for a release; no mod class is ever loaded, so absent mods stay safe.

**Rejected:** one Java adapter per mod (closed to pack authors); generic "find every id" rewriting (cannot tell outputs from inputs).

## ADR-017 - Scripts (KubeJS) have the last word

**Order of application:** datapacks (Material Nexus generated pack included, at the top) are parsed first; KubeJS recipe and tag events then edit the result in memory. So for the same recipe or tag, **KubeJS wins**: a script that removes, replaces or re-adds something applies on top of what Material Nexus generated. This is intended: a script is an explicit author decision, more specific than a unification policy.

Consequences, made visible rather than hidden:
- Material Nexus analyzes the final game state (tags after scripts, recipes after scripts) for discovery and for detecting which recipes touch an alternative, so its view matches what players get.
- It only rewrites recipes that exist as files; a recipe created by a script has no file to override and is listed in Preview as "not handled yet" (fix it in the script, or let in-world conversion handle the items).
- A script matching an alternative by item id (`replaceOutput('ie:ingot_tin', ...)`) stops matching once that recipe was rewritten to the canonical item; scripts matching by tag are unaffected. Prefer tags in scripts, or unify after scripting.

**Rejected:** fighting scripts (re-applying after KubeJS, mixins into its events): two tools silently overriding each other is the failure mode this project exists to remove.
