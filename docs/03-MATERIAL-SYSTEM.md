# 03 — Material system

## Discovery

Material discovery combines:

- item/block registry contents;
- common Forge/common material tags;
- explicit Material Nexus definitions;
- integration-provided semantic metadata;
- conservative name/form conventions.

Name similarity alone is insufficient for automatic unification. `dark_steel` and `steel` are not merged without an explicit alias or trusted metadata.

## Initial forms

`ore`, `raw`, `block`, `ingot`, `nugget`, `dust`, `plate`, `rod`, `gear`, `wire`, `sheet`, `coil`.

## Canonical selection

For every material/form the resolver produces:

```text
canonical provider
alternatives
reason
policy source
confidence
```

Example explanation:

> Copper Plate → `create:copper_sheet` because the pack policy gives Create priority for PLATE and an explicit form override exists.

## Unification actions

Depending on the form, a policy may:

- choose canonical item;
- add canonical material tags;
- add/remove duplicate provider tags;
- redirect recipes through tags;
- rewrite recipe outputs to the canonical item (explicit, off by default, vanilla types in V1 — ADR-011);
- disable duplicate output recipes;
- leave a provider untouched.

Never remove an item merely because another provider is canonical.
