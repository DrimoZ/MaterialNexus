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

Default when Almost Unified is detected and `global.json` has no `almost_unified` section: every domain is left to AU (it unifies by default), Material Nexus generates nothing there, and Preview lists each domain left to AU. Give a domain to Material Nexus explicitly, e.g. `"almost_unified": {"tags": "mnx", "output_rewrite": "mnx", "recipe_disable": "mnx", "viewer_hiding": "au"}`. In-world conversion and conversion recipes are not AU domains and stay with Material Nexus. Not verified against a running AU (no NeoForge 1.21.1 build found on Modrinth when implemented).
