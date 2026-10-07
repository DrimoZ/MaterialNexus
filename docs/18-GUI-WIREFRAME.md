# 18 — GUI layout (implemented, MNX-009 redesign)

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Material Nexus  94 materials      [Unify all suggestions] [Revert] [Preview (N)] │
├──────────────────┬──────────────────────────────────────────────────────────┤
│ [search…]        │ Aluminum                                                 │
│ ● Aluminum       │ [Forms] [Recipes] [Missing]              [Unify material]│
│   12 forms · 5   │ ┌──────────────────────────────────────────────────────┐ │
│ ● Copper         │ │ Ingot  Suggestion: click an item…        Recipes ›   │ │
│ ○ Coal           │ │ No policy: strongest evidence, then alphabetical id  │ │
│ …                │ │ [MI][IE] [variant…]                                  │ │
│                  │ └──────────────────────────────────────────────────────┘ │
│ [<] Page 1/2 [>] │                                                          │
└──────────────────┴──────────────────────────────────────────────────────────┘
```

- **Sidebar**: server-side search and pages; dot = green (every duplicated form decided), amber (duplicates only suggested), grey (nothing to unify).
- **Materials / Forms switch** (MNX-035): the sidebar lists forms instead; the right side then shows one card per material for that form (all ingots, all rods…), with the same pending choices, "Unify form" and Preview. "Recipes" on a card jumps back to that material.
- **Forms**: one card per form; every provider is an icon. Gold border = current canonical, green = pending choice, grey = alternative, dark red = not unified. Clicking an icon makes it the pending canonical (again to cancel). Tooltips give the item, id, role and, for the canonical, Why / source / confidence. "Recipes ›" opens the form's recipe family.
- **Recipes**: form chips; every loaded recipe producing the form with type and status (canonical, alternative, variant, rewritten, disabled by MNX, not handled yet).
- **Missing**: missing standard conversions (proposals only, ADR-003).
- **Top bar**: unify all suggestions, revert last apply, preview with pending count. Read-only sessions (dedicated server) hide editing.
