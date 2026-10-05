# 18 — GUI wireframe

```text
┌──────────────────────────────────────────────────────────────────┐
│ MATERIAL NEXUS                                      Reload  ⚙     │
├───────────────┬──────────────────────────────────────────────────┤
│ Dashboard     │ Materials                                         │
│ Materials     │  Search: [ copper________________ ]              │
│ Recipes       │                                                  │
│ Diagnostics   │  Copper   ✓ Unified    12 forms   4 mods         │
│ Presets       │  Tin      ! Partial     8 forms   3 mods         │
│               │  Steel    ! Conflict    7 forms   4 mods         │
│               │                                                  │
│               │  [Open Copper]                                    │
└───────────────┴──────────────────────────────────────────────────┘

Copper
┌───────────┬──────────┬──────────┬───────────┐
│ Overview  │ Items    │ Tags     │ Recipes   │ ...
└───────────┴──────────┴──────────┴───────────┘

PLATE
Canonical: create:copper_sheet     [Change]
Why? Create priority + explicit form policy

Providers
✓ create:copper_sheet       canonical
○ mod_a:copper_plate        alternative
○ mod_b:copper_plate        duplicate

Recipe families
✓ Pressing       Create
○ Crafting       Vanilla
! Rolling        Mod B      conflict

[Preview changes] [Apply policy]
```
