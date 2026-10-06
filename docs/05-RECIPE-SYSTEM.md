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
| disable | vanilla types, only a rewrite that became an exact duplicate | override with `neoforge:false` condition |
| rewrite output and inputs | every type described by a recipe format (ADR-016; shipped: vanilla, Create, Mekanism, IE, MI) | full recipe JSON at the same ID; literal `"item"` alternatives in inputs become the canonical item, tag inputs untouched |
| rewrite output | types without a format (e.g. Create sequenced assembly) | `UNSUPPORTED` until a format describes them |

Output rewriting happens for forms unified by a player decision and is always previewed. Vanilla types are rewritten from their pre-MNX JSON (read beneath the generated pack, ADR-010); modded types producing an alternative are listed as "not handled yet" and left untouched until their adapter exists. No generic JSON rewriting.

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

### Implemented (MNX-010)

For each material, the expected conversions between the forms it has (nugget↔ingot, ingot↔block, raw↔raw block, raw→ingot, dust→ingot) are checked against every loaded recipe: a conversion is present if any recipe makes an item of the target form from an ingredient accepting an item of the source form (any provider, any recipe type exposing its result). Missing ones are listed at the top of the material detail as proposals. Computed when the detail is opened; nothing is ever generated from them (ADR-003). Recipes that do not expose their result through vanilla APIs (e.g. Mekanism) do not count as evidence, so a proposal can be conservative.
