# 04 — Tags

Tags are first-class data, not an implementation detail.

## Goals

- unify equivalent inputs through material tags when possible;
- keep explicit output items deterministic;
- preserve mod-specific tags unless policy says otherwise;
- report conflicts rather than silently deleting tags.

## Example

```text
#c:ingots/copper
#c:plates/copper
#c:ores/copper
```

A canonical `create:copper_sheet` may become the preferred member of the plate family while alternative items remain available according to the selected policy.

## Safety

Tag edits are previewed. A policy can choose `append`, `replace`, `remove`, or `leave` for an explicit tag family.
