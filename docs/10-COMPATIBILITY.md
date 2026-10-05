# 10 — Compatibility

## Integration boundary

```text
IntegrationAdapter
  ├── CreateAdapter
  ├── MekanismAdapter
  ├── ImmersiveEngineeringAdapter
  ├── ThermalAdapter
  ├── JeiAdapter
  └── EmiAdapter
```

An adapter can contribute semantic evidence, recipe-family hints and viewer hooks. It cannot mutate the core model directly.

## Optional dependency rule

Every adapter is safe when its mod is absent. No optional API class may be loaded by common code without a presence guard.

## Almost Unified

Detected, not integrated. The global policy assigns each domain (`tags`, `output_rewrite`, `recipe_disable`, `viewer_hiding`) to MNX or AU. MNX disables its own actions in AU-owned domains, reports overlaps in diagnostics and never edits AU's config (ADR-012).

## Priority

Integrations provide evidence and provider priorities; explicit pack policy always wins.
