# 07 — GUI

The GUI is a first-class product feature, not a config screen. Its design, the reasons behind it and the measures it
was checked against are in `22-UI-REDESIGN.md`; this page says what the screen is today.

## Access

`/materials` or the creative-only "Nexus Terminal" item. Permission level 2, re-checked server-side. Editing targets
singleplayer/LAN; dedicated servers get read-only diagnostics (ADR-013).

## Screen size

The layout needs 640x360 GUI pixels. When the player's GUI scale leaves less (the default 854x480 window at Auto gives
427x240), the screen uses the largest smaller scale that fits, and puts the player's scale back when it closes
(MNX-075). The widths of the regions follow the GUI width (`client/ui/Regions`): rail, list, table and inspector; under
900 px the inspector is laid over the table.

## Shell

- Top bar: where the player is, as a path, and the search.
- Rail: Overview, Materials (with the number of forms to decide), Forms, Scripts (when scripts decide something);
  under "Advanced", Rule files and Presets; at the foot, Help (the wiki), Shortcuts and Settings.
- Bottom bar: what is not applied yet, "View", "Discard all" and "Review and apply". Nothing is written before Apply.

## Views

### Overview

One sentence on where the pack stands, the one thing to do next, three figures that open the filtered material list,
the mod priority, the history with Restore, and apart from the rest "Revert last apply" and "Reset everything", each
behind a confirmation. Just after an apply, a summary is shown there, with the restart notice when items were created.

### Materials

A list of materials and, for the selected one, its forms in foldable groups: to decide, not applied yet, unified, then
"No duplicate" and "Forms that can be created", folded. Each row shows the kept item and the replaced and left-alone
ones. The inspector of the selected row gives every candidate its name, mod, use in recipes, the reason it is kept or
left alone, and its actions as buttons: keep, not the same item, take out of the tag. Clicking an item in the table
keeps it at once; right click (not the same) and Shift + right click (tag) remain as shortcuts.

### Recipes (tab of a material)

Every loaded recipe making one form, grouped by what applying does to it. Material Nexus draws no recipe: the selected
one is drawn by the installed viewer, through `client/RecipeViewer` and its adapter in `client/compat` (JEI today).
Without a viewer the list stays, without the drawing. A disabled recipe that is applied is no longer loaded, so the
list is the only place it appears.

MNX-117: a bin at the end of each removable row (always shown, R2; red once removed) and, in the inspector's footer,
**Remove this recipe** (danger variant, on the right, away from "Open in"), a
pending `disabled_recipes` edit like any other; **Put this recipe back** undoes it. Refused, with the reason in the
inspector, when the recipe has no file the generated pack can override (`RecipeFamilies.Removal`).

MNX-118: a recipe replaced by one of the author's datapacks names it in the inspector (`Row.editedBy`); one a datapack
switches off is its own group, "Switched off by a datapack". The form inspector lists datapack edits of the form's tag
under "Why?" (`MaterialDetailPayload.packTags`); Review lists `pack_conflict` warnings under "To look at".

### Forms

The materials × forms grid, each cell with a mark as well as a colour, materials without duplicates left out by
default. A form opens as the same table as Materials (one row per material) and a "How it's made" tab: the routes of
the form's process rule, two switches said in words, and the routes found in the pack grouped by machine.

### Sort duplicates

Every form to decide, one at a time, as cards with the key that keeps each. Same pending changes as anywhere else.

### Review and apply

One side panel in two steps. "Your changes": what was asked, removable line by line. "Effects on the pack": what
applying would do, asked from the server when the tab opens, grouped by gravity (to look at, changes, undone):

- recipes disabled, recipe types not handled, script conflicts, Almost Unified overlaps;
- tags, rewritten recipes, converted and created items, data edits;
- what is undone from the current pack.

Apply writes the policy, regenerates the pack and reloads; the screen says so until the server reopens it.

### Scripts (MNX-076)

One row per form the scripts decide: the item they keep, what Material Nexus would keep, and the state (differs, same,
undecided, recorded). The evidence (script file and line, recipes touched) is in the inspector. "Keep" makes the
scripts' item a pending choice. Read-only servers see it without buttons.

### Presets

The selected preset against the current settings, value by value. "Use this preset" only fills pending changes.

### Rule files

The forms, process template, recipe format, preset and material files, edited as JSON; text that does not read as JSON
cannot be kept and the reason is shown. "Untagged forms" declares a name pattern as a form.

### Settings

The player's own, client only, in `config/materialnexus/screen.json`: theme (four, Graphite by default), density,
technical ids, groups open by default, reopening where the screen was left.

## Not there yet

Recipe editor, loot tables, a pivot by mod, free groups (docs/22 section 9.3).
