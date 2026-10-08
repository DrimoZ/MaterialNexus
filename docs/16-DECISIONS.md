# 16 — Architecture decisions

## ADR-001 — No KubeJS dependency

**Decision:** Material Nexus works independently. An optional KubeJS bridge may be added later.  Added in MNX-067: a read-only `MaterialNexus` script binding, loaded only when KubeJS is installed (see 10-COMPATIBILITY).

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
- What scripts decided is read and proposed as choices, never applied silently (ADR-022, MNX-076).

**Rejected:** fighting scripts (re-applying after KubeJS, mixins into its events): two tools silently overriding each other is the failure mode this project exists to remove.

## ADR-018 - Process rules are written by example

**Decision:** a process rule (MNX-036) says how a form is made for every material: routes `machine, input form, in → out`, plus `exclusive` (recipes making the form any other way are disabled) and `enforce_ratio` (a recipe of a route machine with another ratio is replaced; otherwise only missing routes are generated). A route is generated by copying a recipe of that machine that already makes the form from that input for another material (the material's own recipe first when replacing it), swapping every reference to that material (tags for tags, items for the canonical item, conditions included) and setting the counts. The copy is read back: if the counts are not exactly the requested ratio (a shaped pattern, a count stored somewhere unknown) the next example is tried, and with none left the route is listed in Preview as unsupported; nothing is guessed.

**Why:** a recipe format only says where outputs are; writing a new recipe for a modded machine needs its exact JSON shape (molds, energy, fluids, wrappers). An existing recipe of the same machine for the same form carries all of it, for any mod, with no code and no template to maintain.

**Consequences:** balance-affecting, so rules exist only in the policy (`processes` in `global.json`, per-material `process` overrides) and every generated or disabled recipe is listed in Preview (ADR-003). Process recipes are not an Almost Unified domain. A rule disabling a recipe wins over a unification rewrite of it. Machines with no example recipe for that form, or ratios a copy cannot carry (a shaped pattern), use a process template (MNX-037): data in `material_nexus/process_templates`, tried after the examples unless it says `prefer`. Every recipe Material Nexus writes, copied, templated or rewritten, is first decoded by the game; one that fails is not written and Preview lists it as `recipe_invalid`.

**Rejected:** per-machine Java writers (closed to pack authors); templates first (a file to write per machine before anything works).

## ADR-019 - Items for missing forms are registered at startup from config

**Decision:** Material Nexus can create an item for a form a material lacks (a netherite rod), for the item forms that have a template texture (23 since MNX-065: ingot, nugget, dust, plate, rod, gear, wire, gem, raw, double and large plates, bolts, rings, blades, rotors... never blocks or ores). The list lives in `config/materialnexus/items.json`; each entry becomes `materialnexus:<material>_<form>`, registered at startup, named from the material and form, drawn with the form's grayscale template tinted with the material colour (`color`, or the average colour of the `color_from` item's texture). A small pack rebuilt from the registered items at every pack scan (`config/materialnexus/created_items`) puts each one in its convention tag, so discovery sees it as that material's form; recipes then come from process rules (ADR-018), never automatically.

**Why:** registries are frozen after startup; an item cannot appear on /reload. Templates plus a tint need no asset per item and work for any material.

**Consequences:** an entry may name its own `texture`, drawn untinted, and a resource pack can replace the generated model of any created item: only the template layer (layer 1) is tinted (MNX-073). Creating an item needs a restart after Apply; Revert does not remove it (the registry cannot change on a reload). On a server, every client needs the same `items.json` (ship it with the modpack), or the registries will not match. Removing an entry deletes that item from every world that has it.

**Rejected:** registering items lazily or per world (impossible after the registry freeze); per-item asset files (a resource pack to maintain for every material).

## ADR-020 - Names tell variants from duplicates

**Decision:** within one form, a candidate whose id names a variant (a word left once form words and the material are removed: Remin's `yellow_amethyst` tagged `c:gems/amethyst`, IE's `plate_duroplast` tagged `c:plates/plastic`, Silent Gear's `netherwood_stick` tagged `c:rods/wooden`, Create's `crushed_raw_iron` tagged as raw iron) or another known material (`minecraft:quartz` tagged `c:gems/milky_quartz`) is listed as not unified, with the word that gave it away. Words close to the material (golden for gold, aluminium for aluminum) and the words of the material's vanilla items (lazuli for lapis) do not count; vanilla ids are never variants. The rule only applies when a plainly named candidate exists.

**Why:** a tag says "usable as", not "the same item"; mods tag their variants under the base material. Names are the only other evidence available without per-mod code.

**Consequences:** a set-aside item can still be chosen explicitly (click it); "Unify all suggestions" never picks it. Wrong calls are visible in the GUI and the report with their word.

## ADR-021 - Applied states are kept and can be restored

**Decision:** every apply, revert and restore keeps a copy of `config/materialnexus/policies/` as it is right after it (`config/materialnexus/history/<epoch millis>/`, the 20 newest), recorded in `history.jsonl`. The Home view lists the entries; any older entry whose copy is kept can be restored, after a confirmation. A restore writes the copy back behind the usual backup, so "Revert last apply" undoes it, and is itself an entry.

**Why:** "Revert last apply" (one level, ADR-009) cannot go back past a second apply; trying several settings means losing the earlier ones.

**Consequences:** this supersedes the "no history" part of ADR-005 for the player's own settings only: nothing about the world, inventories or items is recorded. Data edits (`policies/data`) come back with a restore; created items do not (registry, ADR-019). Copies are small (the policy files only).

**Rejected:** a diff log replayed on demand (fragile against hand edits); unlimited copies.

## ADR-022 - Decisions found in scripts are proposed, never applied

**Decision:** at each data load Material Nexus reads what scripts changed (item tags as the files define them vs the final tags; with KubeJS, the recipes its event removed, changed or added, through the official `beforeRecipeLoading` plugin hook) and deduces, per material/form, which item the scripts keep (docs/20). Those decisions are shown with their evidence (Home, Scripts view, `/materials report`), offered as ordinary pending choices, and Preview warns when Apply would unify around another item. Nothing is applied without the player's choice.

**Why:** a pack that unified by hand before installing Material Nexus must not get the opposite choice by default (two tools fighting, ADR-017); and to delete its scripts the author needs the decision recorded in the policy first.

**Consequences:** read-only, like ADR-017 requires: scripts still have the last word, nothing hooks their events to change them. Groups come from the file tags, so an item scripts removed from a tag is still known. KubeJS fields read are public but not a stable API; a failure skips recipe edits with one warning. Tag edits by other mods in memory read as script edits.

**Rejected:** applying script decisions automatically (a player decision is required, CLAUDE.md); parsing script files (loops and helpers make it unreliable); a mixin recording the calls (fragile, and the plugin hook gives the same result); parsing KubeJS logs.

## ADR-023 - Missing convention tags are added on request, where the pack uses them

**Decision:** with `"add_missing_tags": true` in `global.json` (Data view, Untagged forms tab), Apply adds each item Material Nexus knows as a form but that lacks its convention tag (found by name pattern, or through an alias tag: `c:ingots/aluminium` members join `c:ingots/aluminum`) to that tag, and to the folder tag (`c:ingots`) when the pack has one. Only where the pack already uses the convention (the tag or its folder tag exists), only for the items unification keeps (the canonical one; every duplicate when the form is not unified). Written as appended `values` in the same generated tag file as the removals; each addition is a `tag_add` effect in Preview and the manifest. On reload, discovery takes them back out like the removals (ADR-010).

**Why:** a recipe asking for `#c:ingots/tin` refuses an ingot a mod forgot to tag, although Material Nexus already knows what it is; adding the tag is what pack authors otherwise do by script.

**Consequences:** off by default (a player decision, like every pack change). Nothing is added when Almost Unified owns tags. Variants, items marked not the same and excluded forms never get a tag. Pattern-only forms nobody tags (`c:double_ingots`) stay untagged: no convention is invented. A tag only Material Nexus filled is dropped from the pre-MNX view, as it did not exist.

**Rejected:** a general tag editor (KubeJS does that); creating `forge:` tags (1.21 uses `c:`); creating every material/form tag in advance (empty tags confuse viewers and other mods).

## ADR-024 - The player edits a form's tag, not any tag

**Decision:** `"tag_edits": {"tin/ingot": {"add": [...], "remove": [...]}}` in `global.json` puts items in or out of that form's convention tag (`c:ingots/tin`), and nothing else. GUI: Shift + right click on an item of a form takes it out (or undoes the edit); the Data view's Untagged forms tab adds an item by id. Apply writes them in the generated tag file (added entries are optional, so an item whose mod left never breaks the tag), after and over the generated additions and removals. Discovery sees added items as members; removed items stay in sight, listed as not unified ("taken out of its tag by you"), so the edit can be undone where it was made.

**Why:** mods forget tags or tag a variant as the base material; fixing one item should not need a script. Keyed by material/form so the edit says what the item is, and the tag follows the folders known to discovery.

**Consequences:** nothing is written when Almost Unified owns tags. A form with no convention tag folder ignores its edits. Removing an item from a tag also keeps it from being unified there; it keeps its other tags (`c:ingots`).

**Rejected:** editing any tag by id (KubeJS does that; ADR-023); hiding removed items (the edit could only be undone by hand in the file).
