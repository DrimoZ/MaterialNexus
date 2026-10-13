# 22 — UI redesign (analysis and design, MNX-093)

Design file for a rewrite of the Material Nexus screen. **No Java is written from this document until it and the
mockup are validated.** It is a separate track from the support backlog (`21-SUPPORT-BACKLOG.md`, MNX-086 to 092),
which is not reordered by it.

- Trigger: tester feedback that the menus are "not polished, clean or clear enough, too much AI slop".
- Scope decided on 2026-10-10: visual system **and** structure (space, priority of information and actions).
- Mockup (private, sixteen boards): https://claude.ai/artifact/RzWYbbeva1Tzi4AHnSErwB
- Reference screen for every measure below: **1280 × 685 GUI px** (2560 × 1370 window at GUI scale 2).
- Audited code: `client/`, 3,610 lines in 22 files; `Ui.java` (95 lines) is the only shared base.

The mockup has not been compared to the game: it is HTML at GUI-pixel sizes, with the mod's own item templates
tinted as stand-in icons. Its fonts are substitutes (see 4.3).

## 1. Rules kept

| # | Rule | Source |
|---|---|---|
| R1 | A table serves four tasks: find, compare, view or edit one row, act. First column is a readable name, never a technical id | NN/g, Data Tables |
| R2 | Edit a row in a **non-modal side panel**; no action hidden behind hover or a gesture | NN/g, Data Tables |
| R3 | One row height per table, header included; hover always shown | IBM Carbon, Data table |
| R4 | No status indicator when no action is expected: plain text. Never colour alone. Past five or six indicators on screen the eye gives up | IBM Carbon, Status indicators |
| R5 | Reduce clutter without reducing capability (staged disclosure); make what matters salient **by removing** the rest | NN/g, Complex applications |
| R6 | Hierarchy: three sizes at most, three contrast levels at most; space around a group detaches it; a heading sits next to what it names; squint test | NN/g, Visual hierarchy |
| R7 | De-emphasise labels in favour of data; three greys; a fixed spacing scale; space and background rather than borders; design in grey, colour last | Refactoring UI (secondary summaries) |
| R8 | Dark theme: dark grey, not black; depth reads through **opaque surface steps**, not transparent veils; few accents | Material Design 2 and 3 |
| R9 | Colours named by **role**, never a raw value in a view | GitHub Primer |
| R10 | One primary button per view; destructive is its own variant, away from the safe path, confirmed; label is verb + object | Coloplast, GitLab Pajamas, Maersk |
| R11 | Text at 4.5:1 or more; control outlines and glyphs at 3:1 or more | WCAG 2.2 AA |
| R12 | High density is justified for an expert comparing many items; 4 px grid | Material density, VTEX Shoreline |
| R13 | Learning by doing: the result shows at once, nothing is lost; shortcuts are offered, not required | NN/g, Complex applications |

## 2. Who does what, how often

The user is a pack author working in short sessions. Every priority below derives from this table.

| Rank | Task | Frequency | Today |
|---|---|---|---|
| T1 | Decide which item to keep for a duplicate | every session, dozens of times | click an 18 px icon, no name or mod shown |
| T2 | Check what will change, then apply | every session | good: bottom bar and drawer |
| T3 | Understand why an item is kept or set aside | often; it is what blocks (the electrotine case) | tooltip only |
| T4 | Overrule a guess of the mod | sometimes | right click, Shift + right click, unannounced |
| T5 | Work in bulk (mod priority, unify all, preset) | once per pack | three places (Home, Presets, header) |
| T6 | See recipes, set how a form is made | sometimes | raw ids |
| T7 | Go back | rare, critical | a button of the same weight as the others |
| T8 | Edit rule files (JSON), declare forms | rare, expert | a first-level rail entry |

The screen is built around T1 → T3 → T2. Everything else is secondary.

## 3. Audit

### 3.1 Use of space (1280 × 685, Materials › Amethyst)

| Zone | Size | Share | Real occupation |
|---|---|---|---|
| Top bar | 1280 × 24 | 3.5 % | title and three pills over ~440 px, search 130 px, **700 px empty** |
| Rail | 78 × 637 | 5.7 % | five entries, 90 px tall: **86 % empty** |
| Material list | 170 × 637 | 12.4 % | full, well used |
| Detail zone | 1019 × 637 | 74 % | see below |
| Bottom bar | 1280 × 24 | 3.5 % | text on the left, two buttons on the right, 950 px empty between |

In the detail zone: row content stops at ~292 px; the next **~690 px are empty**, then "Recipes ›" at the far
right, ~780 px from the row name it belongs to. Twenty rows of 26 px, of which **three are useful** and seventeen
read "Absent / Create item". Informative surface ≈ 292 × 78 px, **2.6 % of the screen**; empty surface ≈ 41 %.

Data view: the JSON editor is 928 × 517 px (55 % of the screen) for ~280 × 290 px of content.

Diagnosis: fixed-width columns (`LABEL 112`, `STATUS 84`) packed left, one element pinned right. Nothing adapts,
nothing is capped, nothing takes the freed space. Vertically it is the reverse: 26 px per row for 9 px text.

### 3.2 Priority of information today

| Information | Weight for T1 to T3 | Where it is |
|---|---|---|
| Name of the candidate item | critical | tooltip |
| Source mod | critical (the criterion of the choice) | tooltip |
| Reason an item is set aside | critical | tooltip, in pink |
| Why this item is kept (source, confidence) | high | tooltip |
| Use in recipes (produced / used) | high | tooltip |
| How many forms are left to decide | high | good: header, list, top bar (three times) |
| "Single item", "Nothing to unify", "Absent" | none (nothing to do) | a pill on every row, an 84 px column |
| Forms that could be created | low | seventeen full-height rows |
| Pack-wide counters | medium | top bar, always, plus the Home cards |
| Technical id (`mod:path`) | low, useful to experts | foreground in Process, Recipes, Data, Scripts |

The order is inverted: what decides is hidden, what asks nothing fills the screen.

### 3.3 Priority of actions (full inventory from the code)

| Action | Frequency | Risk | Today | Problem |
|---|---|---|---|---|
| Choose the kept item | very high | none (pending) | click an icon | no label; cancel by clicking again |
| Review and apply | high | high, previewed | blue button, bottom right | white on blue: **2.89:1** |
| See pending changes | high | none | clickable text, bottom left | does not look like a button |
| Mark "not the same" | medium | none | right click | invisible |
| Take out of the tag | low | medium | Shift + right click | invisible |
| Open a form's recipes | medium | none | link at the far right | too far |
| Unify this material / form | medium | none | amber button in the header | amber is the warning colour, used for the recommended action |
| Unify all suggestions | once | none (pending) | stone button, Home | same weight as four others |
| Triage | once | none | stone button, Home | excellent keyboard mode, not findable |
| Mod priority | once | none | 12 px ▲ ▼ × +, Home | tiny targets |
| Revert last apply | rare | **high** | stone button, Home | same weight, no confirmation |
| Reset everything | very rare | **high** | stone button, Home | right under "Unify all" |
| Restore a past state | very rare | **high** | 12 px "↺" | confirmed, but a tiny target |
| Put a choice back to default | rare | none | grey "↺" on the row | glyph only |
| Create a missing item | rare | medium (restart) | a button on seventeen rows | repeated seventeen times |
| Process: ratios, exclusive, enforce | rare | medium | stone "−/+", "Only these: OFF" | cryptic labels, four buttons with no unit |
| Data: keep, drop, original | rare | medium | three stone buttons | three button styles in one screen |

### 3.4 Visual consistency

| Finding | Evidence |
|---|---|
| Three button families (vanilla stone, flat blue, flat grey) and a black field with a white border | 32 vanilla widgets in five files |
| ~50 distinct hard-coded colours, 100 occurrences outside `Ui`; five link blues (`88AAFF`, `88BBFF`, `6AABF6`, `5A9BE6`, `AAAAFF`) | `grep 0x` |
| Duplicate greens and ambers (`55FF55` and `4CC46A`; `FFCC33`, `FFAA33` and `E8B23A`) | `RecipesPanel`, `ProcessPanel` |
| Backgrounds made of stacked translucent white veils (`0x14`, `0x18`, `0x30`, `0x40` + `FFFFFF`): the real tone depends on what is underneath | everywhere |
| Heights with no scale: 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 26 | `ROW`, `LINE`, `HEAD` constants |
| One text size, plus a blurry ×2 for figures | `card`, `TriagePanel` |
| Status carried by colour alone: list edge, slot outline, grid cells | `MaterialSidebar`, `Ui.slot`, `FormMatrix` |
| Grid headers rotated 90° | `FormMatrix` |
| Form chips of the Recipes tab cut at the edge | `RecipesPanel` line 79 |
| Scrolling with no visible bar; Process scrolls by rebuilding widgets | `scroll()` |

### 3.5 Measured contrast (WCAG formula, on the current background `101014`)

| Current colour | Use | Ratio | Verdict |
|---|---|---|---|
| `FFFFFF` TEXT | data | 18.98 | passes; harsher than needed |
| `9A9AA6` MUTED | secondary | 6.82 | passes |
| `66666F` FAINT | column headers, hints | 3.34 | **fails** |
| `5A5A66` NEUTRAL | "Single item" pill text | 2.79 | **fails** |
| `3A3A44` ABSENT | "Absent" pill text | 1.69 | **unreadable** |
| white on `5A9BE6` | "Preview and apply", "Use this preset", "Keep" | 2.89 | **fails** |
| `D9534F` DANGER | warning text | 4.79 | borderline |
| dark item (`2B2B2B`) on the slot well `22222A` | coal, blackstone, netherite icons | 1.1 to 1.3 | **the icon disappears** |

### 3.6 What works and stays

Rail, content and a pending-changes bar. Everything goes through Preview before Apply. Item icons as values. The
grid as an overview. Keyboard triage. The Preview drawer grouped by kind. The GUI scale fit (`fitScale`, 640 × 360).

## 4. Design system

### 4.1 Theme

The blue-black of the current screen is not a choice: it is the default of developer tools. What the content of
this screen imposes:

| # | Finding | Consequence |
|---|---|---|
| 1 | The screen shows hundreds of icons of every hue, and T1 is comparing two nearly identical items. A tinted surround shifts colour perception (simultaneous contrast); image software uses **neutral** greys for that reason | Greys with no tint. The blue cast is a functional error, not a taste |
| 2 | A dark item on a dark well measures 1.1 to 1.3:1. On the vanilla slot grey `8B8B8B`: 4.2:1 for a dark item, 3.0:1 for a light one. Textures are drawn for that grey | The **slot well is mid grey** in every theme |
| 3 | NN/g: at normal vision light mode reads better, most of all for small text; do not force dark on everyone, offer it | The screen opens in game, often over a dark scene: dark by default, **not near black**, light as an option |
| 4 | No pure black, no pure white; saturated colours vibrate on dark and must be lightened | Off-white text, softened meaning colours |
| 5 | The mod logo: a silver, copper and gold hexagon around an emerald | The identity already exists |

Candidates, contrast computed on `bg` / `surface` / `raised`:

| | A. Neutral graphite | B. Workshop, mid grey | C. Paper, light |
|---|---|---|---|
| `bg` / `surface` / `raised` | `1C1C1C` / `252525` / `2F2F2F` | `2B2B2B` / `353535` / `404040` | `F3F3F1` / `FFFFFF` / `E7E7E4` |
| `text` | `EAEAEA` 14.2 / 12.7 / 11.1 | `F0F0F0` 12.4 / 10.8 / 9.1 | `1F1F1F` 14.8 / 16.5 / 13.3 |
| `muted` | `ABABAB` 7.4 / 6.7 / 5.8 | `BDBDBD` 7.5 / 6.5 / 5.5 | `565656` 6.6 / 7.3 / 5.9 |
| `faint` (never on `raised`) | `8C8C8C` 5.1 / 4.6 / 4.0 | `A3A3A3` 5.6 / 4.9 / 4.1 | `6B6B6B` 4.8 / 5.3 / 4.3 |
| control outline | `747474` 3.6 / 3.3 | `8A8A8A` 4.1 / 3.6 | `8A8A8A` 3.1 / 3.5 |
| emerald | `5CC878` 8.1 / 7.3 / 6.4 | `6FD389` 7.7 / 6.6 / 5.6 | `1F7A3A` 4.8 / 5.4 / 4.3 |
| gold | `E8B23A` 8.8 / 7.9 / 6.9 | `EDBE55` 8.2 / 7.1 / 6.0 | `8A5A00` 5.3 / 5.9 / 4.8 |
| red | `EA6E69` 5.6 / 5.1 / 4.4 | `F0827E` 5.5 / 4.8 / 4.0 | `B3261E` 5.9 / 6.5 / 5.3 |
| steel blue | `6AA7F2` 6.8 / 6.1 / 5.4 | `7DB4F5` 6.6 / 5.7 / 4.8 | `1F5FBF` 5.5 / 6.1 / 4.9 |

D, the current blue-black cleaned up (`0F1115` / `171A21` / `1F232C`), is kept in the mockup as a comparison only.

Meaning colours, taken from the logo, each doubled by a shape because green, gold and red merge for colour-blind
players:

| Meaning | Colour | Shape |
|---|---|---|
| Kept, unified, main action | emerald | tick |
| To decide | gold | filled dot |
| Destructive, "not the same", error | copper red | cross |
| Choice not applied yet, information, link, focus | steel blue | ring |

Text on a colour fill is dark (`141414`: 7.4:1 on emerald, 6.8:1 on steel blue), never white. Meaning colours cover
less than 5 % of a normal screen.

**Decided (2026-10-10):** the four themes ship, **A is the default**, the player picks one in Settings (6.1).
Emerald is the primary button colour (green reads as the action that validates); steel blue is kept for "not
applied yet" and focus. Each theme keeps its own slot well grey (`7A7A7A` for A, `8B8B8B` for B and C; D keeps the
current dark well).

### 4.2 Spacing and sizes (GUI px)

- Spacing: **2 · 4 · 8 · 12 · 16 · 24**, nothing else. 4 inside a component, 8 between components of a group, 16
  between groups, 24 around a view.
- Heights: text row **16**; row with an item **22** (18 px slot + 2 + 2); button, field, tab, filter **18**; top
  bar **24**; bottom bar and view header **28**.
- Button width: text + 2 × 8, 48 minimum. Smallest click target **16 × 16** (12 × 12 today).
- Square corners: the game does not draw a clean radius at this size.

### 4.3 Text

Three levels, by colour and weight, never by a ×2 scale: title (`text`, bold), body (`text`), secondary (`muted`
or `faint`). Numbers right-aligned in columns. Long text is cut with "…" and complete in the tooltip.

Font is a player setting (decided 2026-10-10): the game's pixel font by default, a bundled vector font through a
`ttf` font provider as an option, once its rendering is checked at each GUI scale. The mockup shows both with
substitutes (Pixelify Sans for the pixel font, IBM Plex Sans for the vector one).

### 4.4 Statuses

| State | Mark | Colour | Shown |
|---|---|---|---|
| To decide | filled dot + text | gold | yes |
| Not applied yet | ring + text | steel blue | yes |
| Unified | tick + text | emerald | yes, quiet |
| No duplicate, absent | none | `faint` | text only, in a folded group |

### 4.5 Components

One copy of each, with rest, hover, selected, keyboard focus and disabled states.

| Component | Variants | Notes |
|---|---|---|
| Button | primary (colour fill, dark text), secondary (`raised`), ghost (text), danger (red text; red fill only inside the confirmation) | one primary per view |
| Field | text, search, multi-line | `raised` fill, control outline, focus outline in steel blue |
| Tabs | underlined | 18 tall |
| Segmented filter | All / To decide / Unified | one active |
| Toggle | switch with its label on the right | replaces "Only these: OFF" |
| Stepper | `− 2 +` with the unit | replaces four anonymous buttons |
| Table | fixed header, foldable groups, visible scrollbar | one row height |
| List row | icon, name, right-aligned count | icon place kept even when empty |
| Item slot | rest, hover, not applied yet (ring), left alone (dimmed), not the same (cross) | mid-grey well |
| Inspector / drawer | title, scrolling body, action footer | one template for the form detail, Review, Pending |
| Count badge, banner, empty state, confirmation | | |
| Tooltip | complement only (technical id, cut text) | never the only source of a useful fact |

## 5. Layout and space budget

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Material Nexus › Materials › Amethyst                    [ Search…         ] │ 24
├──────────┬────────────────┬──────────────────────────────┬───────────────────┤
│ RAIL     │ LIST           │ TABLE                        │ INSPECTOR         │
│ 104      │ 176            │ fluid, 404 to 640            │ 264 to 360        │
├──────────┴────────────────┴──────────────────────────────┴───────────────────┤
│ ○ 2 changes not applied yet [View]                [Discard] [Review and apply] │ 28
└──────────────────────────────────────────────────────────────────────────────┘
```

| GUI width | Real case | Rail | List | Table | Inspector |
|---|---|---|---|---|---|
| 640 (minimum) | 1280 × 720 at scale 2 | 88 | 148 | 404 | 264, **over** the table on selection |
| 960 | 1920 × 1080 at scale 2 | 104 | 176 | 416 | 264, fixed |
| 1280 | 2560 × 1440 at scale 2 | 104 | 176 | 640 | 360, fixed |
| beyond | ultra-wide | 104 | 176 | 640 | 360, then a right margin |

Table and inspector grow together up to their caps; past that a margin is left rather than stretching. No element
is pinned to the right edge away from what it concerns.

Vertical, at 685: 24 + 28 + 18 + 16 + 28 = 114 px of structure, 571 px left, **25 rows of 22 px** (22 rows of 26
today). With rows that need no decision folded, Amethyst is three rows and two group headers.

| Empty today | Becomes |
|---|---|
| 690 px right of the table | the inspector: name, mod, reason, use, actions (T1, T3, T4) |
| 700 px of the top bar | breadcrumb on the left, a 200 px search on the right; no pills |
| 86 % of the rail | a Work / Advanced split, count badges, help at the foot |
| Seventeen "Absent" rows | one folded row "Forms that can be created (17)" |
| The 84 px "Status" column | removed; the mark takes 16 px before the name |

## 6. View by view

P1 is always visible, P2 on selection, P3 on demand.

### 6.1 Shell

- Top: clickable breadcrumb (replaces "‹ All forms"), search. "Read only" badge on a dedicated server.
- Rail, Work group: Overview, Materials (badge: forms to decide), Forms, Scripts (when present). Advanced group:
  Rule files, Presets. Foot: Help, Shortcuts, **Settings** (theme, font; client-side, per player).
- Bottom: mark, "N changes not applied yet", **View**; on the right **Discard** (ghost) and **Review and apply**
  (the one permanent primary). With nothing pending the bar is one `faint` line and the button is disabled with
  its reason written.

### 6.2 Overview (was Home)

| Level | Content |
|---|---|
| P1 | one state sentence and **one** primary action that depends on the state: nothing decided → "Start sorting"; some left → "Resume sorting (12)"; all decided → "Review and apply" |
| P1 | three figures in a row, each opening the filtered list: to decide, unified (chosen / by priority), left alone |
| P2 | Mod priority: an ordered list, 16 px controls, "Add" on unranked mods, one line of explanation |
| P2 | Secondary bulk actions: "Unify all suggestions", "See all forms" |
| P3 | History: dated lines, a labelled "Restore", confirmed |
| P3 | Sensitive zone, apart at the bottom: "Revert last apply", "Reset everything", both confirmed |
| removed | the three grey tip paragraphs |

### 6.3 Materials

- List: segmented filter, rows (icon, name, count to decide). Materials with no duplicate in `muted`. Scrolling
  with a bar instead of pages.
- Header: name in bold, "26 forms · 2 to decide". **Unify this material** (secondary, only when something is left),
  a "…" menu for "back to default" and "discard pending here".
- Tabs: Forms, Recipes, Missing conversions (with a count; hidden at zero).
- Forms table: mark + **Form** | **Kept** (slot, item name, mod) | **Replaced · left alone** (slots).
  Groups: "To decide" and "Not applied yet" open at the top, "Unified" open, "No duplicate" and "Forms that can be
  created" folded. The fold preference is remembered.
- Selecting a row opens the **inspector**: one card per candidate with icon, **name**, **mod**, recipe use, id in
  `faint`, its role in words ("Left alone: named after another material, electrotine"), and labelled buttons:
  **Keep this one**, **Not the same item**, and on a left-alone item **Same item after all** (the gesture MNX-086
  asks for). Foot: **See recipes**, **Back to default**.
- Clicking a slot in the table still keeps that item at once (R13); right click stays as a shortcut, now announced.

### 6.4 Recipes tab

Decided 2026-10-10: **no hand-made recipe renderer**. The recipe is drawn by the installed viewer (this is
MNX-088's design).

- Rows: machine (name and icon from the viewer's recipe category), output slot, state as mark and text. A rewritten
  recipe shows the replaced output dimmed next to the kept one. Groups by state: rewritten, disabled, not handled,
  unchanged (folded). Form picker as chips that wrap, plus a machine filter.
- Inspector of the selected recipe: the **viewer's own layout** (same background, slots and tooltips as in JEI or
  EMI), then "What Material Nexus changes" (output replaced by the kept item), "Why" (the choice that causes it,
  with a link to it), technical ids, and "Open in JEI / EMI".
- Everything viewer-specific lives in `client/compat/jei` and `client/compat/emi` (docs/06). No viewer installed:
  the list stays, with the recipe type and output, no drawing.
- Limit to state in the UI: the viewer draws what the game has loaded. Before Apply that is the original recipe;
  after Apply a disabled recipe is no longer loaded, so the row is the only place it appears.
- Needs nothing new from the server: `RecipeFamilies.Row` already carries recipe id, type, output and status.

### 6.5 Forms: the grid

Horizontal column headers on two lines. Cells carry a mark as well as a colour. Hovered row and column are
highlighted, headers stay fixed, scrollbars are visible. "Only materials with duplicates" is on by default.
Clicking a cell opens the material on that form.

### 6.6 Forms: one form, how it is made (was Process)

- "Materials" tab: the table of 6.3, one row per material.
- "How it is made" tab: a state sentence; "Routes of the rule" with machine name, labelled Input and Output
  steppers, Remove; "Routes found in the pack" **grouped by machine**, with Add and the technical id in `faint`;
  two toggles in plain words, each with its consequence; a side preview on one material and the list of what the
  rule disables.

### 6.7 Triage

Same principle. Reached from the Overview primary action and the "To decide" filter; candidate cards share the
inspector's grammar; the key is shown on each card; progress "4 / 12"; the end offers "Review and apply".

### 6.8 Review (was Preview) and pending changes

One drawer, two tabs: **Your changes** (what was asked, discardable line by line) and **Effects on the pack**.
Effects are grouped by gravity, not by technical kind: "To look at" (recipes disabled, recipe types not handled,
script conflicts), then "Changes", then "Undone from the current pack". Counts right-aligned, lines with item
icons. Footer: a one-line summary, **Apply** (primary), Close. Progress is shown in the drawer during compute and
apply; the result is a summary in the screen, not a chat line. A restart need is a banner.

### 6.9 Scripts, 6.10 Presets

Scripts: rows in the common grammar, state as mark and text, "Take this choice"; the evidence (script file and
line, recipes touched) moves to the inspector. Presets: list and detail, "Current" against "With this preset", one
action followed by its effect in words.

### 6.11 Rule files (was Data)

Under Advanced, with a banner saying who it is for. Foldable groups, readable names with the id as secondary, a
"modified" mark. Editor capped at ~560 px, monospace, line numbers, the JSON error under the editor with its line.
A side panel says what the file does, its fields, and the items it recognises. "New file" opens a small form.
"Untagged forms" is a scrolling table.

## 7. Vocabulary

One term per notion, everywhere. To validate, in both languages:

| Today | Proposed (en / fr) | Why |
|---|---|---|
| Kept / canonical | Kept / Gardé | one word; "canonical" leaves the interface |
| Alternatives | Replaced / Remplacés | says what happens to them |
| Set aside / not unified | Left alone / Laissés tels quels | "set aside" explains nothing |
| Suggestion / to decide | To decide / À décider | one term |
| Pending | Not applied yet / Pas encore appliqué | explicit |
| Single item, Nothing to unify | No duplicate / Sans doublon | one notion, one word |
| Process rule | How it's made / Fabrication | |
| Data | Rule files / Fichiers de règles | says what it is |
| Preview and apply | Review and apply / Vérifier et appliquer | |
| Triage | Sort duplicates / Trier les doublons | |

Short sentences, verb first on buttons, no exclamation mark, no technical id inside a sentence.

## 8. Interaction and feedback

- Keyboard: Tab through controls, arrows in lists and tables, Enter for the row's main action, Escape closes the
  drawer then the inspector. Visible focus.
- Loading: skeleton rows in the zone concerned. Empty: one sentence and one action. Error: next to what failed,
  with what to do.
- After Apply: the screen reopens on a summary, with "Revert this apply" there.
- A visible scrollbar wherever content overflows.

## 9. Built to evolve

### 9.1 Seven invariants

| Invariant | What it allows later |
|---|---|
| **Three levels of values**: primitives → roles (`bg`, `text`, `accent`…) → components. A view reads roles only | a theme is another role table: light, high contrast, colour-blind, or one read from a resource pack |
| **One scale** of spacing and heights | a density setting is a second set of values |
| **One copy of each component** | a button is fixed or improved in one place |
| **Fixed regions**: rail, list, main, inspector, bottom bar, drawer. A view declares what goes in each | a new view does not draw its own layout |
| **A table described by its columns and groups** | a column, a sort, a grouping, multi-selection, without touching views |
| **An inspector made of stacked sections** | a new fact or action on an item is one more section |
| **List, table and inspector do not assume "material × form"**: they show a group, its rows, its candidates | other things to unify (free tags, fluids) reuse the template |

Plus a state contract: every view provides loading, empty, error and read-only.

Capacity: the rail holds about ten entries in two groups; a view holds five tabs; a view header holds one visible
action and a "…" menu.

### 9.2 Interface improvements that fit afterwards

| Improvement | Where it lands | Cost once the base exists |
|---|---|---|
| Light, high-contrast, colour-blind themes | role table | low |
| Compact / comfortable density | scale | low |
| Theme from a resource pack | reading a role file | low to medium |
| Undo / redo of pending choices | `PendingChanges` | medium |
| Multi-selection and a batch bar | table | medium |
| Column sort and choice; saved filters; pinned materials | table, list | low |
| Global search with grouped results; command palette | top bar | medium |
| Shortcut panel, contextual help, glossary, first-run guide | rail foot, inspector, Overview | low |
| Resizable panels | regions | medium |
| Non-blocking notifications | shell | low |
| Player notes on a choice | inspector section + policy file | medium |
| Narrator, text size | components | medium |

### 9.3 Features that may be asked for, and where they go

| Likely request | Origin | Place |
|---|---|---|
| Overrule a set-aside ("same item after all") | MNX-086 | button on the item card, inspector |
| Recipes as in JEI, open in JEI / EMI | MNX-088 | recipe row component; button provided by the compat adapter |
| Which mods and recipe types are handled | MNX-090 | a "Compatibility" view under Advanced |
| Textures of created items | MNX-091 | a "Created items" tab |
| Declare a tag folder as a form | MNX-092 | a form in Rule files |
| Unify any tag (circuits, silicon, rubber) | parked, ADR first | a "Free groups" rail entry on the same template |
| Pivot by mod | missing today | a third pivot next to Materials and Forms |
| Mod priority per form or material | extension | an inspector section |
| Fluids, blocks, world-generated ores, loot tables | domain extensions | new objects in the template; material tabs |
| Recipe editor | docs/07, V2 | an edit drawer from a recipe row |
| Compare two applies, detailed log | history | a History view once the list grows |
| Export / import choices, share a preset | packs | Presets actions |
| `/materials report` inside the screen | support | Compatibility or a Diagnostics view |

None of these changes the template. That is the test of the structure. None is built ahead: only the invariants
of 9.1 are laid from the start.

## 10. Feasibility notes (read only, nothing run in game)

### 10.1 Minecraft 1.21.1 (checked in `neoforge-21.1.248-sources.jar`)

- `AbstractButton.renderWidget` is protected and overridable: a restyled button stays a real widget (focus,
  narration, keyboard).
- `EditBox` has `setBordered(false)`, `setTextColor` and `setHint`: the field background can be drawn by the screen.
- `AbstractScrollWidget` exposes `renderBackground`, `renderBorder` and `renderDecorations`: the multi-line editor
  can lose its black box. `AbstractSelectionList` exposes `renderListBackground`, `renderListSeparators` and
  `renderSelection`.
- `TrueTypeGlyphProviderDefinition` is present: a bundled vector font is possible through a font JSON.

Not checked: the same hooks on Forge 1.20.1, and how a vector font renders at each GUI scale.

Recipe viewers (method names read in the API jars the build already compiles against, nothing run):

- JEI 19.57 (`jei-1.21.1-common-api`): `IRecipeManager.createRecipeLayoutDrawable` returns an
  `IRecipeLayoutDrawable` with `setPosition`, `drawRecipe`, `drawOverlays`, `getRect` and
  `getItemStackUnderMouse`; `IRecipeCategory` gives `getTitle`, `getIcon`, `getWidth`, `getHeight`.
- EMI 1.1.24: `EmiApi.getRecipeManager().getRecipe(id)` returns an `EmiRecipe` with `addWidgets(WidgetHolder)`,
  `getDisplayWidth`, `getDisplayHeight` and `getCategory`; embedding means providing a `WidgetHolder`, more work
  than JEI. `EmiApi.displayRecipe` opens it in EMI.

To confirm in game: how to get from a recipe id to the viewer's recipe object for modded categories, and the
layout size against the 360 px inspector.

### 10.2 Data the views need and the server does not send

| Need | Today |
|---|---|
| Recipe drawing, machine name and icon in the Recipes tab | from the recipe viewer on the client (6.4), nothing new from the server |
| Machine name and icon in "How it is made" and Review, where no viewer recipe is at hand | only the type id; process templates could carry a display item and a name |
| Effects with enough to draw icons | `PackContent.Effect` is `(kind, target, item)`: one item, enough for most rows |
| Item name, mod, recipe use, reason of a set-aside | already sent (`MaterialDetailPayload`, `usage`, `notUnified`) |
| Totals, history, mod list | already sent |

## 11. Open points

Settled on 2026-10-10: four themes with A as default; emerald primary; each theme's own slot well; font as a
player setting; Rule files stay in the screen; use the available space rather than hide content; a click on a
slot keeps that item at once and opens the inspector; "No duplicate" and "Forms that can be created" are folded
by default; recipes are drawn by JEI or EMI, not by hand.

The mockup now has sixteen boards: the ten first ones plus Recipes, Sort duplicates, Scripts, Presets, Settings
and a States board (your changes, confirmation, empty, error, loading, after apply).

1. The wording of section 7 is adopted in full, in both language files (decided 2026-10-10).
2. Settings: all of the mockup's are kept (decided 2026-10-10), as far as each proves feasible: theme, font,
   density, three display toggles (technical id under item names, groups open by default, reopen where I left)
   and the viewer choice when JEI and EMI are both installed.
3. Interface glyphs (search, tick, arrows): font glyphs or a small texture sheet.
4. Where the Settings are stored (a client config file).

Then: a second plan for the code (one place for roles and components, order of the views, port to `forge-1.20.1`).

## Sources

- https://www.nngroup.com/articles/data-tables/
- https://www.nngroup.com/articles/complex-application-design/
- https://www.nngroup.com/articles/visual-hierarchy-ux-definition/
- https://www.nngroup.com/articles/dark-mode/
- https://carbondesignsystem.com/components/data-table/usage/
- https://carbondesignsystem.com/patterns/status-indicator-pattern/
- https://primer.style/product/getting-started/foundations/color-usage/
- https://m2.material.io/design/color/dark-theme
- https://material.io/blog/tone-based-surface-color-m3
- https://material.io/design/layout/applying-density.html
- https://spectrum.adobe.com/page/color-system
- https://creativepro.com/color-management-the-effect-of-our-environment/
- https://design.gitlab.com/components/button
- https://designsystem.maersk.com/components/button/
- https://www.designtokens.org/tr/drafts/format/
- WCAG 2.2, success criteria 1.4.3 and 1.4.11
