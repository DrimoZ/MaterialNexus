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

Default when Almost Unified is detected and `global.json` has no `almost_unified` section: every domain is left to AU (it unifies by default), Material Nexus generates nothing there, and Preview lists each domain left to AU. Give a domain to Material Nexus explicitly, e.g. `"almost_unified": {"tags": "mnx", "output_rewrite": "mnx", "recipe_disable": "mnx", "viewer_hiding": "au"}`. In-world conversion and conversion recipes are not AU domains and stay with Material Nexus. Verified against Almost Unified 1.21.1-1.4.2+neoforge (MNX-066): `./gradlew runGameTestServer -Pau` loads it and checks that a full preview leaves every AU domain alone. With AU, recipe outputs are already unified by AU at load, so Material Nexus finds fewer recipes to rewrite even in domains given to it.

## KubeJS

Optional (MNX-067). When KubeJS is installed, scripts get a read-only `MaterialNexus` binding:

| Call | Answer | Source |
|---|---|---|
| `MaterialNexus.kept(item)` | the item kept in place of `item`, or `item` itself | the applied pack |
| `MaterialNexus.isAlternative(item)` | true when `item` is converted to another one | the applied pack |
| `MaterialNexus.conversions()` | map alternative id to kept id | the applied pack |
| `MaterialNexus.canonical(material, form)` | the kept item id, or null | the policy as resolved at the last reload |
| `MaterialNexus.alternatives(material, form)` | the other items of that form | the policy as resolved at the last reload |

**Decisions found in scripts (MNX-076, ADR-022, docs/20).** Material Nexus also reads what scripts changed: tag entries added or removed in memory (any script tool), and with KubeJS the recipes its event removed, changed or added (plugin hook `beforeRecipeLoading`). It deduces which item scripts keep per material/form, shows it (Home, Scripts view, report), offers it as a pending choice, and Preview warns when Apply would keep another item. Dev check: in the `-Pkubejs` GameTest run, the fixture script `materialnexus_unify.js` makes two decisions that the GameTest `scriptDecisionsAreRead` expects.

"The applied pack" is the manifest of the generated pack, so these answers are right inside recipe events, which run before Material Nexus reads the new tags. Example: `event.shaped(MaterialNexus.kept('thermal:tin_ingot'), [...])`. Scripts still have the last word (ADR-017). Dev check: `./gradlew runGameTestServer -Pkubejs` runs `src/gametest-fixtures/kubejs/server_scripts/materialnexus_check.js` (its line is in `run-gametest/logs/kubejs/server.log`).
