# Changelog

## Unreleased

- Preview and Apply are faster on large packs: recipe files are listed once, read once and parsed in parallel, and rewrites copy less (full Preview of the 1.21.1 dev pack from 1.9 s to 0.8 s).
- Process rules are planned in a fraction of the time: each form looks only at the recipes that make it (dev pack: 400 ms to 35 ms).

## 0.3.0

- The screen is redesigned. A path at the top, a menu on the left (Overview, Materials, Forms, then Rule files and Presets under Advanced) and one bar at the bottom for what is not applied yet. Four themes.

- Overview: one sentence for the state of the pack, one main action, three figures that open the filtered list, the mod priority with labelled buttons. Reverting and resetting sit apart and ask for confirmation.

- Materials: the forms of a material in groups that fold (To decide, Not applied yet, Unified, No duplicate, Forms that can be created), and an inspector listing every item of a form with its name, its mod, how many recipes make and use it, why it is kept or left alone, and its actions as buttons.

- Review and apply is one panel in two steps: your changes, removable line by line, then the effects on the pack, grouped by what needs a look first.

- Recipes are drawn by the installed viewer, JEI or EMI: in the inspector, and next to the pointer on a recipe row or on a route of How it's made. The list is grouped by what applying does, and a replaced item is struck through next to the kept one.

- Forms grid, Sort duplicates, Scripts and Presets redrawn: a mark in every cell of the grid, candidate cards with their key while sorting, one row per script decision with its evidence, a preset shown next to your settings.

- Rule files (formerly Data): a JSON error is shown with its line and the edit cannot be kept until it reads. A tag folder no form reads yet (`c:coils/<material>`) can be declared as a form from the Untagged forms tab.

- An item is only "named after" a material it is tagged as, and a guess can be overruled: **It is the same item** makes an item left alone a candidate again (`same` in `global.json`).

- A created item is drawn with `config/materialnexus/textures/<material>_<form>.png` when the file is there, nothing to declare.

- New default drawings for created items: 25 templates redrawn so the tint reads as the material, and the double plate as a form. The previous, softer drawings stay available from the screen's Settings.

- The generated pack no longer breaks a world when a mod is removed after Apply: each recipe it writes loads only while every item it names exists.

- `/materials report` ranks the recipe types no format covers by the recipes naming a material item, so the mods worth a format are known.

- Screen settings: technical id under item names, groups open by default, reopening where the screen was left, and a smooth font (IBM Plex Sans; the game font stays the default).

## 0.2.0

- New logo.

- Forms removable as data: `"remove_folders": ["sheetmetals"]` in a forms file (`material_nexus/forms`) stops reading a convention tag folder, built-in ones included.

- `/materials report` has a Tags section: items missing their tag (what "Add missing tags" adds), recipes asking for a material tag with no item, recipes still using `forge:` tags.

- Tag edits: Shift + right click an item of a form to take it out of that form's tag (`c:ingots/tin`), or add an item by id in the Data view; written on Apply, listed in Preview, undone the same way.

- Missing tags: an option (Data view, Untagged forms tab) adds items Material Nexus knows by name pattern or alias to their convention tag (`c:ingots/tin`, and `c:ingots`) on Apply, so recipes asking for the tag accept them. Only where the pack already uses that tag; listed in Preview.

- Each script decision says where it is written ("seen in unify.js:12", Scripts view and report), found by searching the scripts for the item ids.
- Decisions found in scripts: tag entries changed in memory and, with KubeJS, recipes replaced, removed or created by scripts are read at load; the item scripts keep per form is shown (Home, new Scripts view, `/materials report`) and offered as a pending choice. Preview warns when Apply would keep another item. Lets a pack that unified with scripts move to Material Nexus and delete those scripts.

## 0.1.0 - first public version

- Discovery of material forms from convention tags and from item names (declared patterns), with variants and other materials set aside by name.
- In-game screen (`/materials`, Nexus Terminal): Home with totals, triage, mod priority and history; Materials; Forms grid with process rules; Data editor; Presets.
- Choices, mod priority, "not the same" marks and presets as pending changes, previewed before anything is written; discard per change, per material, per form or all.
- Generated datapack: tag cleanup, recipe output rewrites for vanilla and many modded recipe types (data-driven recipe formats), conversion recipes, in-world conversion of alternatives.
- Process rules: missing machine recipes generated by example or from templates, ratios enforced on request; every written recipe is decoded by the game first.
- Created items for missing forms (23 templated forms, tinted per material), or drawn with the pack's own texture or model.
- Back to default per form, material or whole pack; revert last apply; restore any of the last 20 applied states.
- Item tooltips (what an alternative becomes, the kept item), JEI / EMI hiding, Almost Unified arbitration per domain, KubeJS read-only bindings.
- `/materials report` diagnostics; English and French.
