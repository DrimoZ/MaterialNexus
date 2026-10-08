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

## What Material Nexus writes

- **Removals** (MNX-007): each alternative of a unified form leaves the material tag (`c:ingots/tin`) through a NeoForge `remove` entry.
- **Additions** (MNX-078, ADR-023, `"add_missing_tags": true`): an item known as a form but missing its tag (name pattern, alias tag) is added to `c:<folder>/<material>` and to `c:<folder>` when the pack has it, only where the pack already uses that tag.

- **Player edits** (MNX-079, ADR-024, `"tag_edits"`): items put in or taken out of one form's convention tag, from the GUI (Shift + right click, Data view) or the file; they win over the generated changes.

Both go in one generated tag file per tag (`"replace": false`, `values`, `remove`), are listed in Preview, and are taken back out of the view discovery reads (ADR-010).

## Safety

Tag edits are previewed. Nothing is removed or added without a player decision; only a form's own convention tag can be edited; never a tag outside the convention namespace, never a general tag editor (scripts do that).
