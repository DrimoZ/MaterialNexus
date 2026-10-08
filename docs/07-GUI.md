# 07 — GUI

The GUI is a first-class product feature, not a config screen. It ships in V1.

## Access

`/materials` or the creative-only "Nexus Terminal" item. Permission level 2, re-checked server-side. Editing targets singleplayer/LAN; dedicated servers get read-only diagnostics (ADR-013).

## Screen size

The layout needs 640x360 GUI pixels. When the player's GUI scale leaves less (the default 854x480 window at Auto gives
427x240), the screen uses the largest smaller scale that fits, and puts the player's scale back when it closes
(MNX-075). A window under 640x360 real pixels still gets scale 1, cramped.

## V1 screens

### Material list

Search, sort and filter by status, mod, form and confidence.

### Material detail

Per form: canonical provider, alternatives, policy source, confidence and a `Why?` explanation. The canonical provider can be changed here.

### Recipe family

Shows every variant, source mod, type, classification and the proposed action (keep, disable, rewrite output — vanilla types only in V1).

### Preview → Apply

Diff-like list:

- disabled recipes;
- rewritten recipes;
- tag changes;
- canonical changes;
- viewer changes;
- Almost Unified overlaps;
- forms where Material Nexus would keep another item than the scripts (warning, never written; MNX-076).

### Scripts (MNX-076)

Shown in the navigation rail only when scripts made unification decisions (docs/20). One row per material/form: the item scripts keep, what Material Nexus keeps, the status (recorded / same / differs / undecided) and the evidence (recipe changed, tag entry removed, recipe removed). "Keep" makes the scripts' item a pending choice; "Keep all" does it for every `same` and `differs` row. The Home view links to it with the count. Read-only servers see it without buttons.

Apply writes the policy, regenerates the pack and reloads. "Revert last apply" restores the previous policy. Nothing destructive is applied from a single accidental click.

## V2

- dashboard with unified / partial / conflict / missing / unsupported counts;
- recipe editor (explicit inputs, output, conditions, viewer visibility);
- Loot and JEI/EMI tabs;
- presets.
