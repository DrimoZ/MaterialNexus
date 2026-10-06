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

## Presets (V2)

- `vanilla_priority`
- `create_priority`
- `tech_pack`
- `maximum_unification`
- `minimal_changes`

## What Apply generates (MNX-007)

Only forms unified by a player decision (any policy level; never the "Default" suggestion) generate content:

- each alternative is removed from the material convention tag (`c:ingots/tin`) through a NeoForge `remove` entry; it keeps every other tag;
- for forms listed in `conversion_recipes`, a shapeless 1:1 recipe turns each alternative into the canonical item (`materialnexus:convert/<material>/<form>/...`), so existing stock is never stranded;
- variants and "not unified" providers are never touched.

The manifest lists every effect. On reload, discovery puts back the tag members Material Nexus removed (ADR-010), so the next apply regenerates the same content instead of undoing it. With nothing pending, Preview shows the difference between the policy files and the current pack, which is how hand edits are applied.
