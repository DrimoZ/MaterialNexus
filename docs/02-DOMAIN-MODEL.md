# 02 — Domain model

## Material

A semantic resource such as `copper`, `tin`, `steel`, `brass` or `electrum`.

`MaterialId` and `FormId` are namespace-free names (`[a-z0-9_]+`): every mod's copper is the same material, matching the `c:ingots/copper` tag convention. Concrete items keep their `ResourceLocation`.

```text
MaterialId
MaterialDefinition
  - display name
  - aliases
  - enabled forms
  - preferred provider policy
  - explicit overrides
```

## Form

A shape of a material, not an item:

`ORE, RAW, BLOCK, INGOT, NUGGET, DUST, PLATE, ROD, GEAR, WIRE, SHEET, COIL`

The list is extensible.

## Provider

A concrete resource candidate:

```text
Provider
  namespace:id
  material
  form
  source mod
  confidence
  evidence
  tags
```

## Recipe family

Semantic grouping, e.g. `copper/plate`. A family contains all recipes that produce or consume the same material form for the same purpose.

Classification:

- canonical
- duplicate
- alternative
- conflicting
- unsupported
- generated

## Policy precedence

```text
Global < Material < Form < Explicit Resource Override
```

Within a candidate set, explicit policy beats provider priority; provider priority beats defaults.

## Confidence

1. explicit Material Nexus definition
2. standard material tags/conventions
3. known mod integration metadata
4. conservative naming heuristics

Confidence is this ordered rank (`DiscoveryEvidence.Confidence`), not a numeric score. A provider's confidence is its strongest evidence.

Heuristics can propose a match but never silently mutate the pack.
