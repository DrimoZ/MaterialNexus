# 05 — Recipe system

## Recipe pipeline

```text
Recipe registry
  → normalize recipe shape
  → identify material/form
  → assign family
  → classify variants
  → compare outputs/inputs
  → apply policy
  → emit change plan
```

## Generic adapters first

- shaped crafting
- shapeless crafting
- smelting
- blasting
- smoking
- campfire cooking
- stonecutting
- smithing where the recipe semantics are safe

## Mod-specific adapters

Create, Mekanism, Immersive Engineering and Thermal are separate adapters. Core recipe logic must not contain `if (Mods.create())` branches.

## Actions and their reach (ADR-011)

| Action | V1 reach | Mechanism |
|---|---|---|
| disable | any recipe type | override with `neoforge:false` condition |
| rewrite output | vanilla types only | full recipe JSON at the same ID |
| rewrite output | mod types | `UNSUPPORTED` until the adapter exists |

Output rewriting is explicit, off by default and always previewed. No generic JSON rewriting.

## Duplicate classification

A recipe can be a duplicate when its normalized input/output semantics are equivalent to another recipe in the same family. Exact item identity is not enough; material tags are considered where policy permits.

## Missing recipe detection

A missing family member is a **proposal**:

```text
Copper / plate
  existing: Create pressing
  existing: crafting
  missing: standard tag-compatible route
  proposal: create only if policy enables generation
```

Material Nexus does not decide that a 9-ingot recipe is balanced simply because an ingot-to-block conversion exists elsewhere.

## Recipe editor

A recipe definition contains:

- type/method;
- ordered or unordered inputs;
- output;
- count;
- conditions;
- enabled;
- viewer visibility;
- use-material-tag flag.

All edits produce a preview change plan before application.
