# 08 — Configuration and generated datapack

See ADR-007 and ADR-008.

## Layout

```text
config/materialnexus/
  policies/            source of truth, hand- or GUI-edited
    global.json
    materials/<material>.json
  policies.bak/        previous policy, used by "Revert last apply"
  generated/           build output, overwritten on every Apply
    _GENERATED_DO_NOT_EDIT
    pack.mcmeta
    manifest.json
    data/...
```

`generated/` is injected into every world via `AddPackFindersEvent` (position TOP, always enabled). Hand-made recipe changes go into the author's own datapack/KubeJS, never into `generated/`.

Material definitions shipped by mods or datapacks may also live under `data/<namespace>/material_nexus/materials/`.

## Example material

```json
{
  "id": "copper",
  "forms": ["ore", "raw", "block", "ingot", "nugget", "dust", "plate", "rod", "wire"],
  "aliases": ["copper"]
}
```

## Example policy

`policies/global.json`:

```json
{ "mod_priority": ["minecraft", "create"], "exclude": ["wood", "steel/rod"], "conversion_recipes": ["ingot", "block"] }
```

`policies/materials/copper.json` (file name is free; `material` is the key, defined once):

```json
{
  "material": "copper",
  "mod_priority": ["mekanism"],
  "forms": {
    "plate": { "preferred_provider": "create:copper_sheet" },
    "wire": { "mod_priority": ["immersiveengineering"] }
  }
}
```

Levels map to ADR-006: `global.mod_priority` < material `mod_priority` < form `mod_priority` < form `preferred_provider`. Policy is re-read on every `/reload`; an invalid file fails the analysis with the file named and leaves the previous snapshot active. Action fields (`rewrite_outputs`, tag modes) arrive with their tickets.

## Global policy: Almost Unified arbitration

```json
{
  "almost_unified": {
    "tags": "mnx",
    "output_rewrite": "au",
    "recipe_disable": "mnx",
    "viewer_hiding": "au"
  }
}
```

Only read when Almost Unified is present (ADR-012).

## Generated output

- tags: NeoForge tag files with `remove` entries / appended values;
- disabled recipe: same ID, `"neoforge:conditions": [{"type": "neoforge:false"}]`;
- rewritten recipe: full recipe JSON at the same ID;
- `manifest.json`: every written file, the policy rule that produced it and the reason.

The exact codecs are deliberately versioned with the implementation; the domain contract is more important than freezing JSON prematurely.

## Presets (MNX-019)

`data/<namespace>/material_nexus/presets/<id>.json` holds a partial `global.json`. Applying a preset (top bar "Presets") opens the Preview of the policy with the preset on top; Apply writes its fields into `global.json`, keeps every field it does not define (e.g. `almost_unified`), and is revertable. Names and descriptions come from `materialnexus.preset.<id>` and `.desc` lang keys. Shipped: `vanilla_priority`, `create_priority`, `tech_pack`, `maximum_unification`, `minimal_changes`; pack authors add their own by datapack.

## What Apply generates (MNX-007)

Only forms unified by a player decision (any policy level; never the "Default" suggestion) generate content:

- each alternative is removed from the material convention tag (`c:ingots/tin`) through a NeoForge `remove` entry; it keeps every other tag;
- for forms listed in `conversion_recipes`, a shapeless 1:1 recipe turns each alternative into the canonical item (`materialnexus:convert/<material>/<form>/...`), so existing stock is never stranded;
- variants and "not unified" providers are never touched.

The manifest lists every effect. On reload, discovery puts back the tag members Material Nexus removed (ADR-010), so the next apply regenerates the same content instead of undoing it. With nothing pending, Preview shows the difference between the policy files and the current pack, which is how hand edits are applied.

## Process rules (MNX-036, ADR-018)

```json
"processes": {
  "rod": {
    "routes": [
      { "machine": "immersiveengineering:metal_press", "input": "ingot", "in": 1, "out": 2 },
      { "machine": "minecraft:crafting_shaped", "input": "ingot", "in": 2, "out": 4 }
    ],
    "exclusive": false,
    "enforce_ratio": false
  }
}
```

In `global.json`, per form for every material. A material file can override it for one form with `"forms": {"rod": {"process": {...}}}`; `"routes": []` switches the rule off for that material. Ratios are 1..64. Each generated recipe is `materialnexus:process/<form>/<material>/<input>/<in>/<out>/<machine>`. Edited from the GUI in the form view, "Process" tab.

## Created items (MNX-039, ADR-019)

```json
{ "items": [ { "material": "netherite", "form": "rod", "color_from": "minecraft:netherite_ingot" } ] }
```

`config/materialnexus/items.json`, written by the GUI ("Create a missing form" on a material) or by hand. Forms: ingot, nugget, dust, plate, rod, gear, wire. Optional `"color": "#4D494D"` instead of `color_from`. Read at startup only: restart after a change, ship it to clients, and never remove an entry from a running world unless its items may disappear. A resource pack can replace the templates (`materialnexus:item/template/<form>`).

## Forms and item name patterns (MNX-040)

Discovery reads the convention tags `c:<folder>/<material>` for these folders: ores, raw_materials, storage_blocks, ingots, nuggets, gems, dusts, tiny_dusts, dirty_dusts, clumps, shards, crystals, plates, sheetmetals, rods, gears, wires. A gem is the ingot of non-metals (coal and charcoal are tagged `c:gems/*` by Material Nexus, as vanilla leaves them untagged).

Forms are data too, in `data/<namespace>/material_nexus/forms/*.json`: `folders` adds (or remaps) convention tag folders, `patterns` declares item name patterns for forms mods leave untagged:

```json
{
  "folders": { "ore_chunks": "ore_chunk" },
  "patterns": { "double_ingot": ["modern_industrialization:{material}_double_ingot"] }
}
```

`"relations": [{"from": "tiny_dust", "to": "dust"}]` and `"remove_relations": [...]` add or remove the conversions checked for missing recipes (built-in: nugget/ingot, ingot/block, raw/raw_block, raw→ingot, dust→ingot, gem/block).

Still built in, on purpose: the forms that can be created (they need shipped textures and are registered at startup, before any datapack loads), the "no ingot or wire for a gem material" creation rule (the client applies it too), and ore host rocks (an unknown ground already becomes `<ground>_ore`).

`/materials report` ends with "Possibly untagged forms": undiscovered items named after a known material, grouped by name shape, to review and declare.

Pattern forms: double_ingot, large_plate, curved_plate, bolt, ring, blade, rotor, drill_head, fine_wire (Modern Industrialization shipped, plus its untagged wires); Remin ores and Oritech small dusts are shipped too. An item matches only if `{material}` names a material some convention tag already knows; the match is shown as "item name pattern" evidence and never added to the game's tags.

## Process templates (MNX-037)

`data/<namespace>/material_nexus/process_templates/*.json`, one per machine, used when no recipe of that machine can be copied for a route (or first, with `"prefer": true`):

```json
{
  "machine": "immersiveengineering:metal_press",
  "forms": { "rod": { "mold": "immersiveengineering:mold_rod" }, "plate": { "mold": "immersiveengineering:mold_plate" } },
  "recipe": { "type": "immersiveengineering:metal_press", "energy": 2400,
              "input": { "basePredicate": "${input}", "count": "${in}" }, "mold": "${mold}",
              "result": { "basePredicate": { "item": "${output_item}" }, "count": "${out}" } }
}
```

Placeholders: `${input}` (the input form's tag, else its canonical item), `${inputs}` (that ingredient repeated `in` times), `${in}`, `${out}`, `${pattern}` (from `"patterns": {"3": ["#", "#", "#"]}`, crafting), `${output_item}`, `${output_tag}`, `${material}`, `${form}`, and the form's variables from `forms` (a template with `forms` applies only to those forms). An object key `"${input}"` merges the ingredient into that object (`{"amount": "${in}", "${input}": ""}` for MI and Mekanism stacks). A filled template is kept only if reading it back gives the exact ratio, and the game must decode it.

Shipped: vanilla crafting (shaped, with patterns for 1 to 9 inputs, and shapeless), smelting, blasting, stonecutting; Create pressing, cutting, milling, crushing, compacting; Create Crafts & Additions rolling; Create Metallurgy grinding; IE metal press (plate, rod, gear, wire molds) and crusher; MI compressor, macerator, wiremill, cutting machine, packer (double ingot), forge hammer; Mekanism crushing and enriching; Oritech pulverizer, grinder and assembler.

## Recipe formats (ADR-016)

To let Material Nexus rewrite another mod's recipes, add a file to any datapack (or KubeJS data):

`data/mypack/material_nexus/recipe_formats/othermod.json`

```json
{ "types": ["othermod:grinder", "othermod:press"], "outputs": ["product", "byproducts"] }
```

`outputs` are the top-level keys holding the produced items. To change a shipped format, put a file with the same id (e.g. `data/materialnexus/material_nexus/recipe_formats/create.json`) in a higher-priority pack. Types not covered by any format are listed in Preview as "not handled yet" and left untouched.

## Material definitions and aliases (MNX-033)

`data/<namespace>/material_nexus/materials/<name>.json`, from any datapack:

```json
{ "id": "aluminum", "aliases": ["aluminium"] }
```

Tags named after an alias (`c:ingots/aluminium`, `c:storage_blocks/raw_aluminium`...) are discovered as the declaring material, with the evidence "alias of aluminum" (explicit-definition confidence). Aliases are never guessed from names. An alias claimed by two materials keeps the first claim by file id and is reported in the log. Material Nexus ships `aluminium -> aluminum`. `forms` is parsed but not used yet.
