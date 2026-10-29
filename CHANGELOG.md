# Changelog

## Unreleased

- A recipe is no longer listed as not handled because a tag, a load condition or a group is spelled like an item it unifies (`{"tag": "aether:skyroot_stick"}` next to the item `aether:skyroot_stick`). Found loading 33 more mods: 79 recipes were listed for that reason.
- Dusts that mods tag as grits (`forge:grits/<material>`, Immersive Geology) are found as dusts of that material. Declared as data (`material_nexus/forms/common_tags.json`), so a datapack can drop it or add another folder the same way.
- Conditional recipes (`forge:conditional`, `meadow:conditional` on 1.20.1) are read through: the recipe they hold is rewritten like any other. In the 292 most downloaded technology, magic and equipment mods for 1.20.1 Forge, 395 recipes sit inside such a wrapper.
- More keys are read without a recipe format: `key` (the grid of a shaped recipe), `template`, `base` and `addition` (smithing) and `itemInput` are inputs.
- Recipe formats shipped for Voltex (`industrial_core`) and Tinkers' Construct casting and part recipes: their secondary outputs (`result2`, `byproduct`) and bare-string results are rewritten.
- A recipe format file can now `skip` a recipe type (`{"skip": ["mod:type"]}`): it is never rewritten, by format or by its keys, and is listed as not handled.
- Recipes of any mod are rewritten, not only those with a recipe format: without one, an item under `input(s)`, `ingredient(s)` or `inputItems` is an input and one under `output(s)`, `result(s)` or `outputItems` an output, unless the game reads the recipe otherwise (the Immersive Geology foundry now makes the kept chromium plate). Any other key is left as it is and listed. Review and apply lists these recipes apart, with their type.
- Inputs become the form's tag (`forge:plates/chromium`, `forge:ores/copper`) instead of the kept item, when every item of that tag is the same material, the tag holds the kept item and the game accepts both items there; otherwise the kept item, as before.

## 0.4.0

- Remove a recipe from the screen: the bin at the end of a row in the Recipes tab, or **Remove this recipe** in its inspector. Like every choice it waits for Review and apply, and the same button puts it back, before or after applying (`disabled_recipes` in `global.json`). A recipe a script makes in memory, or one Material Nexus makes from your rules, cannot be removed this way, and the screen says why.
- Your own datapacks are read, never changed: a recipe one of them replaces or switches off says which datapack does, a form's inspector says what they put in or take out of its tag, and Review and apply warns when applying would undo one of their changes.
- Preview and Apply are faster on large packs: recipe files are listed once, read once and parsed in parallel, and rewrites copy less (full Preview of the 24.5k-recipe dev pack from 5.5 s to 3.7 s).
- Process rules are planned in a fraction of the time: each form looks only at the recipes that make it (24.5k-recipe dev pack: 1.8 s to 0.1 s; full Preview now 1.7 s).
- Recipe files are read once after each reload instead of on every preview, keeping only the recipes Material Nexus can read, until the world closes (24.5k-recipe dev pack: reading 1.2 s to 0.1 s per preview, full Preview 0.7 s; the first preview after a reload reads the files, 1.3 to 1.4 s).

## 0.3.0

- The screen is redesigned. A path at the top, a menu on the left (Overview, Materials, Forms, then Rule files and Presets under Advanced) and one bar at the bottom for what is not applied yet. Four themes.

- Overview: one sentence for the state of the pack, one main action, three figures that open the filtered list, the mod priority with labelled buttons. Reverting and resetting sit apart and ask for confirmation.

- Materials: the forms of a material in groups that fold (To decide, Not applied yet, Unified, No duplicate, Forms that can be created), and an inspector listing every item of a form with its name, its mod, how many recipes make and use it, why it is kept or left alone, and its actions as buttons.

- Review and apply is one panel in two steps: your changes, removable line by line, then the effects on the pack, grouped by what needs a look first.

- Recipes are drawn by the installed viewer, JEI or EMI: in the inspector, and next to the pointer on a recipe row or on a route of How it's made. The list is grouped by what applying does, and a replaced item is struck through next to the kept one.

- Forms grid, Sort duplicates, Scripts and Presets redrawn: a mark in every cell of the grid, candidate cards with their key while sorting, one row per script decision with its evidence, a preset shown next to your settings.

- Rule files (formerly Data): a JSON error is shown with its line and the edit cannot be kept until it reads. A tag folder no form reads yet (`forge:coils/<material>`) can be declared as a form from the Untagged forms tab.

- An item is only "named after" a material it is tagged as, and a guess can be overruled: **It is the same item** makes an item left alone a candidate again (`same` in `global.json`).

- A created item is drawn with `config/materialnexus/textures/<material>_<form>.png` when the file is there, nothing to declare.

- `/materials report` ranks the recipe types no format covers by the recipes naming a material item, so the mods worth a format are known.

- Screen settings: technical id under item names, groups open by default, reopening where the screen was left, and a smooth font (IBM Plex Sans; the game font stays the default).

- Thermal Series: its machine recipes (press, pulverizer, smelter, sawmill, centrifuge, insolator...) are rewritten like the others, and process rules can write press (plate, gear), pulverizer and smelter recipes. Tinkers' Construct needs nothing: its casting recipes give the item of a tag, so they follow the kept item once the tag is cleaned.

- New default drawings for created items: 25 templates redrawn so the tint reads as the material, and the double plate as a form. The previous, softer drawings stay available from the screen's Settings.

- The generated pack no longer breaks a world when a mod is removed after Apply: each recipe it writes loads only while every item it names exists.

## 0.2.0

- New logo.

- Forms removable as data: `"remove_folders": ["sheetmetals"]` in a forms file (`material_nexus/forms`) stops reading a convention tag folder, built-in ones included.

- `/materials report` has a Tags section: items missing their tag (what "Add missing tags" adds), recipes asking for a material tag with no item, recipes asking for `c:` tags (Forge 1.20.1 uses `forge:`).

- Tag edits: Shift + right click an item of a form to take it out of that form's tag (`forge:ingots/tin`), or add an item by id in the Data view; written on Apply, listed in Preview, undone the same way.

- Missing tags: an option (Data view, Untagged forms tab) adds items Material Nexus knows by name pattern or alias to their convention tag (`forge:ingots/tin`, and `forge:ingots`) on Apply, so recipes asking for the tag accept them. Only where the pack already uses that tag; listed in Preview.

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
