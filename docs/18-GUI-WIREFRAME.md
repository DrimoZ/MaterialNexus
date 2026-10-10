# 18 — GUI layout (implemented, MNX-094 to MNX-102)

Sizes are GUI pixels. Design and reasons: `22-UI-REDESIGN.md`.

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ Material Nexus › Materials › Copper                       [ Search…        ] │ 24
├──────────┬────────────────┬──────────────────────────────┬───────────────────┤
│ Overview │ [All|To d.|Un.]│ Copper   26 forms · 1 to dec.│ Copper · Dust     │
│ Materials│ ▣ Aluminum     │ Forms  Recipes  Missing      │ ● To decide       │
│ Forms    │ ▣ Copper     1 │ Form    Kept      Replaced   │ Candidates (3)    │
│ Scripts  │ ▣ Iron         │ ▾ To decide (1)              │ ┌───────────────┐ │
│          │ …              │ ● Dust  ▣ Copper… ▣ ▣ | ▣    │ │▣ Copper Dust  │ │
│ Advanced │                │ ▾ Unified (3)                │ │  Mekanism     │ │
│ Rule f.  │                │ ✓ Ingot ▣ Copper… ▣          │ │ [Keep] [Not…] │ │
│ Presets  │                │ ▸ No duplicate (17)          │ └───────────────┘ │
│          │                │ ▸ Forms that can be cr. (1)  │ Left alone (1)    │
│ Help     │                │                              │ …                 │
│ Shortcuts│                │                              ├───────────────────┤
│ Settings │                │                              │ [See recipes]     │
├──────────┴────────────────┴──────────────────────────────┴───────────────────┤
│ ○ 1 not applied yet [View]                    [Discard all] [Review and apply] │ 28
└──────────────────────────────────────────────────────────────────────────────┘
```

| GUI width | Rail | List | Table | Inspector |
|---|---|---|---|---|
| under 900 | 88 | 148 | the rest | 264, over the table on selection |
| 960 | 104 | 176 | 416 | 264 |
| 1280 and more | 104 | 176 | 640 | 360, then a margin |

- **Colours** are roles of the theme in use (`client/ui/Theme`): surfaces, three text levels, and four meanings, each
  with its shape. Green tick: kept, unified, the main action. Gold dot: to decide. Blue ring: not applied yet. Red
  cross: switched off, destructive. Item slots sit on a mid-grey well so dark and light items both read.
- **Sizes**: spacing 2, 4, 8, 12, 16, 24; text row 16, item row 22 (26 in comfortable density), control 18, bars 24 and 28.
- **One primary button** on screen: "Review and apply" when something waits, "Apply" in the review panel, the sorting
  button on the Overview when nothing waits.
- **Widgets**: `client/ui/Btn`, `Field` and `Editor` are the vanilla widgets redrawn (focus, keyboard, narration kept);
  rows, tabs, switches and steppers are drawn by their panel with `Ui.Hits` click areas.
- **Read-only sessions** (dedicated server) hide every editing control.
