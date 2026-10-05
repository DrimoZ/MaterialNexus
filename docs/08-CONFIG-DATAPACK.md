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

```json
{
  "material": "copper",
  "forms": {
    "plate": { "preferred_provider": "create:copper_sheet", "rewrite_outputs": true }
  }
}
```

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
