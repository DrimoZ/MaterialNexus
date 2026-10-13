# 21 — Support backlog (user feedback)

Feedback received after the 0.2.0 release, analysed on 2026-10-10. Each entry keeps the raw message, what
the code already does, the cause or gap, and the proposed work, so nothing has to be analysed twice.
Ticket ids are proposals: none is started, none is in `14-BACKLOG.md` yet.

Analysis was done by reading code, docs and the store captures. **Nothing was run in game**: every
"to confirm" below is a real open point.

## Context to keep in mind

- Two live branches: `main` (NeoForge 1.21.1, convention namespace `c`) and `forge-1.20.1` (Forge 1.20.1,
  worktree `../MaterialNexus-forge-1.20.1`, convention namespace `forge`, tag `v0.2.0+1.20.1`).
  `TagDiscovery.CONVENTION_NAMESPACE` is the only difference in discovery.
- **A screenshot showing `forge:` tags means the reporter is on 1.20.1.** It is not a missing-tag problem.
- `CanonicalResolver` is identical on both branches (one `getFirst()` / `get(0)` line apart): a resolver
  bug is fixed on both.
- All feedback below comes from one tester (pack with ProjectRed and a pack mod "Koynification", 1.20.1).

## Summary

| ID | Kind | Subject | Priority | Size | Branches |
|---|---|---|---|---|---|
| MNX-086 | Bug | Item wrongly set aside as "named after" another material; no way to overrule | P0 | S + M | both |
| MNX-087 | Docs | Store page never says how to open the screen; wiki has no entry by need | P0 | XS | main (store art shared) |
| MNX-088 | UX | Recipes tab shows ids, not recipes | P1 | M | both |
| MNX-089 | UX | Screen legibility pass ("needs polish") | P1 | M, splittable | both |
| MNX-090 | Compat | Thermal; unhandled recipe types ranked in the report | P1 | S per mod | per loader |
| MNX-091 | Feature | Created item texture by file name, no JSON | P2 | S | both |
| MNX-092 | Feature | Declare a tag folder as a form from the Data view | P2 | S | both |
| — | Parked | Free groups: unify any tag (`circuits`, `silicon`) | P3 | L, ADR first | — |
| — | Refused | General tag editor, creating tags | — | — | — |

---

## MNX-086 — Bug: electrotine alloy ingot cannot be unified to the wanted item

> "Ah l'Electrotine Alloy il ne veut pas me le prendre (Pourtant le Red Alloy oui). C'est sensé être
> "Koynification" le bon ingot"

**Facts from the two screenshots (1.20.1).** `projectred_core:electrotine_ingot` and
`koynification:electrotine_alloy_ingot` both carry `forge:ingots`, `forge:ingots/electrotine_alloy`,
`balm:ingots`. Both are discovered as material `electrotine_alloy`, form `ingot`.

**Cause (traced in code, not reproduced).** `CanonicalResolver.Names.odd`, rule `names_other` (MNX-047):
an item whose id, once form words are removed, equals the name of *another known material* is set aside
when a plainly named candidate exists.

| Item | Words left | Result |
|---|---|---|
| `projectred_core:electrotine_ingot` | `electrotine` | a known material (ProjectRed's electrotine dust, to confirm) and not `electrotine_alloy`: set aside, "named after electrotine" |
| `koynification:electrotine_alloy_ingot` | `electrotine_alloy` | the material itself: sole candidate, kept as `only_provider`, nothing to unify |
| `projectred_core:red_ingot` (Red Alloy, works) | `red` | not a material: rule does not fire |

**Why the player is stuck.** An explicit choice only lifts the set-aside for the item chosen
(`notUnified.remove(explicit)`). Choosing Koynification leaves ProjectRed's ingot aside; the only choice
that unifies is keeping ProjectRed's, the opposite of what is wanted. `not_same` exists, its inverse does
not. **No clean workaround today.**

**To confirm with the reporter.** Hovering the ProjectRed ingot in the screen says "named after
electrotine" (`materialnexus.not_unified.names_other`).

**Fix.**
1. Narrow the rule: `names_other` only when the item really is a member of that other material (any form).
   Vanilla quartz tagged as milky quartz stays set aside (it is in the quartz tag); the electrotine ingot
   does not (it is in no `electrotine` tag). `odd()` needs the discovered memberships passed in.
2. Give the player the last word: a "same item after all" mark, inverse of `not_same`, on items set aside
   by a guess (`names_other`, `named_variant`, `same_mod`, `more_specific`). Policy field in `global.json`,
   GUI gesture on the set-aside item, Preview line, en/fr strings.
3. Tests (JUnit, resolver): `x_alloy` ingot from two mods, one named `x_ingot`, with `x` known as a dust:
   both are candidates. The existing milky quartz case stays green.

**Discarded lead.** "Discovery ignores `forge:` tags": true on `main`, irrelevant here (reporter is on
1.20.1 where `forge` is the convention namespace). Do not reopen.

---

## MNX-087 — Docs: commands and wiki link are too low

> "Pour la page de Material Nexus si j'ai une remaque a faire serai de mettre en haut de la description
> du mods les commande et le lien vers le Wiki, plutôt que de le mettre presque en bas. J'ai mis du temps
> a le trouver lol"

**State.** `STORE.md` never says how to open the screen: neither `/materials` nor the Nexus Terminal
appears; only `/materials report`, in the "For pack makers" paragraph. Links are the last line. The wiki
is 14 pages (about 1000 lines), `Home.md` is organised by feature, not by need.

**Work.**
- `STORE.md`: a "Start" block right under the tagline: `/materials` (permission level 2, cheats on in
  singleplayer) or the Nexus Terminal, then Wiki · Getting Started · Commands · Report a bug. Keep the
  bottom links.
- `docs/wiki/Home.md`: an "I want to..." table at the top (open the screen, choose an item, undo, use my
  own texture, a mod's recipes are not handled, run it on a server), each row to one page.
- `docs/wiki/FAQ.md`: add "Can I edit the generated datapack?" and "Can I use my own textures?" (answers
  in MNX-091 below).
- Paste the description again on CurseForge and Modrinth, both loaders' pages.

---

## MNX-088 — UX: recipes shown "JEI style"

> "Dans l'onglet ou il y a les crafts. Est-ce qu'on peut voir les crafts en mode JEI en plus du code ?"

**State.** `client/RecipesPanel` draws, per row: output icon, recipe id, type id, status.
`RecipeFamilies.Row` is `(recipe, type, output, status)`: no ingredient reaches the client
(`RecipeFamilyPayload`).

**Work.** Do not rebuild a recipe renderer; JEI and EMI exist.
- Row becomes `[inputs] → [output]` as icons; the recipe id moves to the tooltip. Inputs: literal items and
  tags outside the output keys (`RecipeFormats` already separates outputs from inputs).
- A rewritten recipe shows before and after: the replaced item struck through next to the kept one. This
  is what no viewer shows.
- Click a row: open that item's recipes in JEI or EMI, from `client/compat/jei` and `client/compat/emi`
  only (docs/06: viewer buttons live in their adapter). No viewer installed: no click.
- Limit to state in the UI: a disabled recipe is not loaded, so the viewer cannot show it; our row is the
  only place it appears.

**Follow-up from the tester (Discord, with a sketch on the Process tab).**

> "au lieu d'ouvrir JEI, possible de faire en sorte d'avoir un visuel du craft quand on passe la souris
> dessus ? ça sera encore plus pratique"

Accepted, and it becomes the main gesture; the click to JEI or EMI stays as the complement (exact machine
rendering).
- Hover a row (Recipes tab) or a route (Process tab): a tooltip drawing the recipe. Crafting table recipes
  as the real 3×3 pattern; machines as a generic `[inputs] → [machine] → [outputs]` with icons and counts,
  never a copy of each mod's GUI.
- A Process route is a rule for every material, not one recipe: the tooltip shows a real example (one
  material) and says so.
- Same data work as above (ingredients sent to the client), one tooltip shared by both tabs.
- To check: a machine icon without depending on JEI (else its name).

**Related bug, same file.** The form chips stop being drawn when they exceed the panel width
(`if (cx + w > x + width) break;`). Copper has 26 forms: most cannot be selected from the Recipes tab
(only through "Recipes ›" on a form row). Wrap or scroll the chips.

---

## MNX-089 — UX: "needs quite a lot of polish"

> "Il va lui falloir quand même pas mal de polish au mod je trouve"

**Reading.** Not "broken": every other remark of this tester is about understanding or finding something
(recipes as ids, wiki not found, textures already possible but unknown, tags hard, an ingot refused with
no visible reason). Polish here means legibility and discoverability. **Ask the tester for the three
moments they got stuck**; the list below is our reading, not their experience.

Points marked (capture) come from `docs/store-art/screen_*.png` and may be an effect of the crop.

| Area | Finding | Source |
|---|---|---|
| Ids and jargon | Raw ids in Process (`modern_industrialization:forge_hammer`), Recipes and Data; no machine name or icon | captures, `ProcessPanel`, `RecipesPanel`, `DataPanel` |
| Ids and jargon | Many terms to learn: form, kept, alternative, set aside, pending, process rule, route, template, format. Home shows "147 items set aside" with no hint whether that is good | `screen_home.png` |
| Ids and jargon | Cryptic toggles: "Only these: OFF", "Enforce ratios: OFF" | `screen_process.png` |
| Ids and jargon | Data view is a JSON editor; fine for pack authors, off-putting as a top-level rail entry | `screen_data.png` |
| Hidden gestures | Click, right click, Shift + right click, hover: nothing on screen announces them | `The-Screen.md`, `DetailTable` |
| Hidden gestures | The reason an item is set aside is only in its hover tooltip (the electrotine case) | `DetailTable` |
| Hidden gestures | No first-run guidance, although operator rights and a singleplayer world are needed | `Getting-Started.md` |
| Noise | Material view: 9 of 12 copper rows are grey ("Single item", "Nothing to unify") | `screen_material.png` |
| Noise | Home: five grey buttons of equal weight; "Reset everything to default" right under "Unify all suggestions" | `screen_home.png` |
| Noise | Process: the same machine listed four times with different ratios, ungrouped | `screen_process.png` |
| Noise | Source mod of an item not visible without hovering, though it is the criterion of the choice | `screen_material.png` |
| Finish | Recipes tab chips cut off (see MNX-088) | code, confirmed |
| Finish | Text clipped on the right (Data), at the bottom (Preview); block row truncated in the material view | (capture), to confirm in game |
| Finish | Apply ends with a chat message; preview over 5 s on the big dev pack with no indicator | `forge-1.20.1` port notes (MNX-081b) |
| Trust | A guess the player cannot overrule | MNX-086 |
| Trust | Restart needed for created items; another mod may throw during reload (IE + Silent Gear): explained in the wiki only | `Troubleshooting.md`, `docs/upstream/` |

**Proposed split, in order.**
1. Set-aside reason written in the row, and a one-line legend of the gestures under the material table.
2. Names and icons instead of ids (machines in Process, recipes in Recipes).
3. Grey rows folded by default ("show single items").
4. Home: primary action distinct, destructive action apart and confirmed.
5. Progress feedback during Preview and Apply.

---

## MNX-090 — Compat: more mods natively

> "Plus de compats ? Tinkers, thermal, et pleins d'autres mods en natif ?"

**State.** Compat is data, no mod class loaded: a recipe format is `{"types": [...], "outputs": [...]}`
(`material_nexus/recipe_formats/`, 14 shipped), a process template is one file per machine (25 shipped).
Without a format, a mod's items are still unified through tags and in the world; only its machine recipes
stay "not handled yet".

**Work.**
- **Thermal**: on the roadmap since the start (MNX-018, the only unchecked box of Phase 4), and the docs
  use `thermal:tin_ingot` as their example. One format file plus templates (pulverizer, press). First to do.
- **Tinkers' Construct**: casting outputs go largely through tags, so tag cleanup probably covers most of
  it. To confirm in game before promising anything.
- **To check first**: that Thermal and Tinkers have a stable build for each loader (NeoForge 1.21.1, Forge
  1.20.1). Not verified.
- **Stop guessing which mods matter**: `/materials report` lists "not handled yet" recipe types ranked by
  recipe count, so users paste the list in an issue.

---

## MNX-091 — Feature: created item textures without JSON

> "Le data pack généré, je peux l'override ? Par exemple pour avoir plus simple pour mes textures ou quoi
> et pas devoir changer dans les json le infos ?"

**Support answer (already true).**
- `config/materialnexus/generated/` is never edited: rebuilt on every Apply, hand changes are lost
  (`Configuration-Files.md`, "The generated datapack").
- Textures do not need it: `"texture"` on the entry in `items.json`; or a model at
  `assets/materialnexus/models/item/<material>_<form>.json` in a resource pack; or a template for every
  material at `assets/materialnexus/textures/item/template/<form>.png` (`Created-Items.md`, "Your own
  texture or model").
- Recipes: the pack's own datapack, or KubeJS, which runs after Material Nexus.

**Gap.** Every route needs a JSON line or a model file. Wanted: drop
`assets/materialnexus/textures/item/<material>_<form>.png` and it is used, untinted, with nothing declared.

**Work.** When the created items pack writes a model, use that texture if it exists; document it; FAQ
entry (in MNX-087).

---

## MNX-092 — Feature: tags from the interface

> "Possible de gérer plus facilement via interface les travails sur des tags ? créer des tags, ajouter à
> la comparaisons/unification des tags genre forge:circuits pour avoir plus simple ?"

**Already there.** Take an item out of its form's tag (Shift + right click), add one by id (Data view,
Untagged forms tab), "Add missing tags", declare a name pattern ("Declare"). All keyed by material/form
(ADR-023, ADR-024).

**Refused on purpose, keep refusing.** A general tag editor and creating arbitrary tags (ADR-023 and
ADR-024, "KubeJS does that").

**Small ticket (MNX-092).** A "declare a tag folder as a form" field in the Data view. The mechanism
exists (`folders` in `material_nexus/forms`), but only by writing JSON.

**Parked: free groups.** The real need behind the message: unify a tag that is not material × form
(`circuits/basic`, `silicon`, `rubber`). Almost Unified does it with a plain tag list; here everything is
keyed by `MaterialForm`. It is the one place a competitor does more. Touches the domain model: ADR first,
and only if the request comes back.
