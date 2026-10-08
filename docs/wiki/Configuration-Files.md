# Configuration Files

Everything Material Nexus decides lives in `config/materialnexus/`. The screen writes these files;
you can edit them by hand too.

```text
config/materialnexus/
  policies/                 your settings: the source of everything
    global.json             pack-wide settings
    materials/*.json        your choices, one file per material
    data/                   your in-game edits of the data files (a datapack)
  policies.bak/             the settings before the last apply (Revert last apply)
  generated/                the generated datapack: build output, never edit it
  created_items/            tags and models of created items, rebuilt at startup
  items.json                items to create for missing forms
  history.jsonl             what was applied, reverted or restored, and when
  history/<id>/             a copy of policies/ after each of the last 20 entries
  report.md                 written by /materials report
```

## global.json

```json
{
  "mod_priority": ["minecraft", "mekanism", "immersiveengineering"],
  "conversion_recipes": ["ingot", "nugget"],
  "not_same": ["remin:yellow_amethyst"],
  "exclude": ["wood", "steel/rod"],
  "processes": {
    "rod": {
      "routes": [{ "machine": "immersiveengineering:metal_press", "input": "ingot", "in": 1, "out": 2 }],
      "exclusive": false,
      "enforce_ratio": true
    }
  },
  "almost_unified": { "tags": "mnx", "output_rewrite": "au", "recipe_disable": "au", "viewer_hiding": "au" },
  "add_missing_tags": true,
  "tag_edits": { "tin/ingot": { "add": ["modx:tin_bar"], "remove": ["mody:tin_ingot"] } }
}
```

| Field | |
|---|---|
| `mod_priority` | mods in order: the first wins every duplicate you have not decided ([Choosing Items](Choosing-Items#mod-priority)) |
| `conversion_recipes` | forms for which a 1:1 crafting recipe turns each alternative into the kept item |
| `not_same` | items never unified with the others of their tags |
| `exclude` | `material` or `material/form` never unified |
| `processes` | process rules per form ([Process Rules](Process-Rules)) |
| `almost_unified` | which domains Material Nexus handles when Almost Unified is installed ([Compatibility](Compatibility#almost-unified)) |
| `add_missing_tags` | items known as a form by name pattern or alias get its convention tag on Apply, where the pack uses that tag ([FAQ](FAQ)) |
| `tag_edits` | items put in (`add`) or taken out of (`remove`) a form's convention tag, per `material/form` ([The Screen](The-Screen)) |

Every field is optional.

## A material file

`policies/materials/copper.json` (the file name is free; `material` is the key, and each material is
declared in one file only):

```json
{
  "material": "copper",
  "mod_priority": ["mekanism"],
  "forms": {
    "plate": { "preferred_provider": "create:copper_sheet" },
    "wire": { "mod_priority": ["immersiveengineering"] },
    "rod": { "process": { "routes": [] } }
  }
}
```

| Field | |
|---|---|
| `mod_priority` | priority for this material, over the global one |
| `forms.<form>.preferred_provider` | your choice: the item kept for this form |
| `forms.<form>.mod_priority` | priority for this form only |
| `forms.<form>.process` | a process rule for this material and form, over the global one |

## items.json

See [Created Items](Created-Items#in-the-files).

## The generated datapack

`generated/` holds tag files (`remove` entries for alternatives), rewritten and generated recipes,
disabled recipes (same id, with a `forge:false` condition), and `manifest.json`, listing every
effect and why. It carries a `_GENERATED_DO_NOT_EDIT` marker: Material Nexus refuses to overwrite a
folder without it. It is rebuilt from `policies/` on every Apply; hand changes there are lost. Put
your own recipe changes in your own datapack or in KubeJS.

## What is safe to delete

- `history/`, `history.jsonl`, `report.md`, `policies.bak/`: only the history, the report and the
  undo of the last apply.
- `created_items/`: rebuilt at startup.
- Not `policies/` (your decisions), `generated/` (what the pack plays with) or `items.json`
  (deleting it removes created items from worlds).
