# 20 — Decisions found in scripts (MNX-076)

## Problem

A pack in development often unifies by hand before Material Nexus is installed: KubeJS scripts replace one mod's tin
ingot by another's in recipes (`replaceOutput`, `replaceInput`), remove alternatives from convention tags, remove
their recipes. Material Nexus reads the game after scripts (ADR-017) but does not know *why* the game looks that way.
Two failures follow:

- **Opposite choice.** Scripts keep tin from mod A; Material Nexus, by mod priority or by default, keeps tin from mod B.
  Apply rewrites recipes to B, scripts rewrite them back to A, and in-world conversion turns A into B. Two tools
  fight, which is the failure this project exists to remove.
- **Nothing to take over.** An alternative a script already removed from its tag is invisible to discovery, so Material
  Nexus cannot take that form over, and the script can never be deleted safely.

## Goal

Read what scripts changed, deduce the unification decisions it implies, and offer them as ordinary pending choices.
The pack author accepts them (Preview, Apply as usual); the decision then lives in the policy, and the script lines
that made it can be deleted without changing the game.

Out of scope: rewriting or deleting scripts, reproducing script changes that are not unification (balance, new
recipes, loot, worldgen), items created by scripts (they stay one more provider), anything at runtime after load.

## What is read

At each server data load (startup, `/reload`), never per tick, never stored to disk:

| Source | How | Needs |
|---|---|---|
| **Tag edits** | item tags as the data files define them (a fresh vanilla `TagLoader` on the same resources: no KubeJS event fires, its hook needs its own registry) compared to the final tags. Entries only in the final tags were added in memory, entries only in the files were removed in memory. | nothing: works for any script tool |
| **Recipe edits** | KubeJS's plugin hook `beforeRecipeLoading` hands over the recipe event; once the reload is done, its recipes are read: `removed` ones, `changed` ones (original JSON vs current JSON), and the ones scripts added (with their script file and line). | KubeJS; nothing is read without it |

Both are summarized into `ScriptChanges` (ids only) the moment discovery runs; the KubeJS event is not kept.

A changed recipe is summarized as the ids that left it and the ids that came in (every quoted `namespace:path` in its
JSON), whatever its format: a `replaceOutput(X → Y)` shows as X gone, Y came. A removed recipe is summarized by the ids
under its output keys (`result`, `results`, `output`, `outputs`).

## Deduction

Groups come from discovery on the **file** tags (before scripts), so an item a script removed from `c:ingots/tin` is
still known as a tin ingot. For each material/form group, signals:

| Signal | Means |
|---|---|
| a changed recipe where X (in the group) left and Y (in the same group) came | for Y, against X |
| X removed from that group's convention tag | against X |
| a removed recipe whose output is X | against X |

Then:

- items with a "for" and no "against" signal: if exactly one, **scripts keep it**; if several, the scripts disagree
  (no decision);
- no "for" signal: if every member but one has an "against" signal, scripts keep the remaining one; otherwise no
  decision (scripts only set items aside);
- an item with both signals is never kept.

Every decision carries its evidence (recipe or tag, from, to), so the screen and the report can say where it comes from.

## Where it is written (MNX-077)

KubeJS records a script file and line only for recipes a script creates; `replaceOutput`, `replaceInput`, `remove` and
tag edits carry none. So, when there are decisions, `kubejs/server_scripts/**/*.js` is searched for the quoted ids
(`'id'`, `"id"`, `` `id` ``) of the items each decision sets aside, comment lines skipped, at most 5 lines per decision.
Shown as "seen in unify.js:12" (Scripts view, report): the lines to delete once the choice is applied. An id built in
code (`'mekanism:ingot_' + metal`) is not found and nothing is shown; nothing is guessed. Rejected: a mixin capturing
the script line at each call (exact with loops, but fragile and against ADR-022).

## Status against Material Nexus

Computed against the current resolution, when shown:

| Status | Condition | Offered |
|---|---|---|
| `recorded` | Material Nexus keeps the same item by an explicit choice | nothing: the script lines can go |
| `same` | Material Nexus keeps the same item, by priority or default | record it (a priority change could flip it) |
| `differs` | Material Nexus keeps another item | keep the scripts' item |
| `undecided` | scripts set items aside but keep none, or disagree | nothing (shown for information) |

## Screen

- **Home**: when scripts decided anything, a line "Your scripts already decide N forms (M differ from Material
  Nexus)" opening the Scripts view.
- **Scripts view** (in the navigation rail only when there is something to show): one row per decision: material,
  form, the item scripts keep, what Material Nexus keeps, status, evidence. "Keep" on a row makes it a pending choice;
  "Keep all" does it for every `same` and `differs` row. Below, the count of recipes scripts created (they stay in
  KubeJS, listed in `/materials report`). Read-only servers see the view without buttons.
- **Preview**: a warning group lists every form where, after Apply, Material Nexus would unify around another item than
  the one scripts keep. Informational: it is never written and does not enable Apply on its own.

## Report

`/materials report` gains a "Script changes" section: the decisions with status and evidence, tag entries added or
removed in memory, and the recipes scripts created with their script file and line.

## Taking over a pack, end to end

1. Install Material Nexus in the pack, scripts untouched.
2. Open the Scripts view, "Keep all", Preview (no warning left for those forms), Apply.
3. Delete the unification lines of the scripts (`recorded` forms). `/reload`: nothing changes in game, Material Nexus now
   does it.

## Limits

- Recipe edits are read from KubeJS only (other script tools: tag edits only).
- A partial replacement (one recipe, for balance) counts as a "for" signal: it is a proposal, reviewed like any other.
- Tag entries changed in memory by other mods (not scripts) are read as script edits; they rarely touch convention
  tags.
- The fields read (`RecipesKubeEvent.originalRecipes`, `addedRecipes`, `KubeRecipe.json/originalJson/changed/removed/
  sourceLine`) are public but not a stable API: checked against KubeJS 2101.7.2; if they change, recipe edits are
  skipped with one warning and tag edits still work.

## Tests

- JUnit: deduction (replacement vote, tag removal leaving one, disagreeing scripts, item in another group untouched).
- GameTest (`-Pkubejs`): a fixture script replaces one tin ingot by another and removes a tin dust from its tag; the
  snapshot holds both decisions with their evidence.
