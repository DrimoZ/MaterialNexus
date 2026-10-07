# Datapack Guide

The rules Material Nexus works from are datapack data, under `data/<namespace>/material_nexus/`. A
pack, a datapack or another mod can add files, or replace a shipped one by using the same id in a
pack loaded later. Changes apply on `/reload`.

| Folder | Says |
|---|---|
| `forms/` | which tag folders and item names make a form, and how forms convert into each other |
| `recipe_formats/` | where a mod's recipes keep their items, so they can be rewritten |
| `process_templates/` | how to write a machine's recipe when there is none to copy |
| `presets/` | ready-made pack-wide settings |
| `materials/` | other names of a material |

## Editing in game

![Data](images/screen_data.png)

The **Data** view lists every one of these files, grouped by kind, with where the version in use comes
from. Editing one saves **your version** in `config/materialnexus/policies/data/` (a datapack named
`materialnexus_user`, loaded on top), as a pending change; **Back to original** removes your version.
Your edits are decoded before being written, and the history keeps them.

## Forms

```json
{
  "folders": { "ore_chunks": "ore_chunk" },
  "patterns": { "double_ingot": ["modern_industrialization:{material}_double_ingot"] },
  "relations": [{ "from": "tiny_dust", "to": "dust" }],
  "remove_relations": [{ "from": "dust", "to": "ingot" }]
}
```

- `folders`: a convention tag folder and the form it holds (`c:ore_chunks/<material>`). Adds to the
  built-in ones or remaps one.
- `patterns`: item ids of a form mods leave untagged; `{material}` stands for a material some tag
  already knows. A match counts as evidence for that form; nothing is added to the game's tags.
- `relations` / `remove_relations`: conversions checked by the Missing tab and the report.

Built-in folders: ores, raw_materials, storage_blocks, ingots, nuggets, gems, dusts, tiny_dusts,
dirty_dusts, clumps, shards, crystals, plates, sheetmetals, rods, gears, wires, double_ingots,
large_plates, curved_plates, bolts, rings, blades, rotors, drill_heads, fine_wires. Built-in
relations: nugget and ingot, ingot and block, raw and raw block, raw to ingot, dust to ingot, gem and
block. Shipped pattern files: Modern Industrialization, Oritech, Realistic Minerals.

The **Untagged forms** tab of the Data view and the end of the report list name shapes worth declaring.

## Recipe formats

```json
{ "types": ["othermod:grinder", "othermod:press"], "outputs": ["product", "byproducts"] }
```

`types`: the recipe types this format describes. `outputs`: the top-level keys holding the items a
recipe produces. With a format, Material Nexus rewrites those recipes (outputs and inputs) when they
involve an alternative; without one, they are listed as "not handled yet" and left untouched.

## Process templates

One file per machine:

```json
{
  "machine": "immersiveengineering:metal_press",
  "forms": {
    "rod": { "mold": "immersiveengineering:mold_rod" },
    "plate": { "mold": "immersiveengineering:mold_plate" }
  },
  "recipe": {
    "type": "immersiveengineering:metal_press",
    "energy": 2400,
    "input": { "basePredicate": "${input}", "count": "${in}" },
    "mold": "${mold}",
    "result": { "basePredicate": { "item": "${output_item}" }, "count": "${out}" }
  }
}
```

| Field | |
|---|---|
| `machine` | the recipe type routes name |
| `prefer` | `true`: use the template before trying to copy a recipe |
| `forms` | variables per output form; a template with `forms` only serves those forms |
| `patterns` | shaped crafting patterns by input count: `{"3": ["#", "#", "#"]}` |
| `recipe` | the recipe, with placeholders |

Placeholders: `${input}` (the input form's tag, else its kept item), `${inputs}` (that ingredient
repeated `in` times), `${in}`, `${out}`, `${pattern}`, `${output_item}`, `${output_tag}`,
`${material}`, `${form}`, and the form's variables. A key `"${input}"` merges the ingredient into
its object: `{ "amount": "${in}", "${input}": "" }` for stacks written that way.

A filled template is kept only if reading it back gives the exact ratio and the game decodes it.

## Presets

A partial `global.json`:

```json
{ "mod_priority": ["mekanism", "minecraft"], "conversion_recipes": ["ingot", "nugget"] }
```

Its name and description come from the lang keys `materialnexus.preset.<id>` and
`materialnexus.preset.<id>.desc`. Using a preset sets the fields it defines and keeps the others.

## Materials

```json
{ "id": "aluminum", "aliases": ["aluminium"] }
```

Tags named after an alias (`c:ingots/aluminium`) are discovered as the declaring material. Aliases
are never guessed from names. Shipped: aluminium as aluminum.
