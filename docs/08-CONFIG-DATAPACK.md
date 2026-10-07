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
