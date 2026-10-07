# Material Nexus

![Material Nexus](src/main/resources/logo.png)

**Unify materials. Understand recipes. Control the pack.**

Material Nexus is a NeoForge 1.21.1 mod for modpack authors. Big packs end up with five copper ingots, three tin plates and a dozen recipes that disagree about them. Material Nexus finds every material form in the pack, shows you the duplicates, and lets you decide which item is kept, all from an in-game screen. Nothing changes until you preview the result and apply it.

## What it does

- **Finds the duplicates.** Every material form is read from the convention tags (`c:ingots/copper`, `c:plates/tin`...) and from item names for the forms mods leave untagged (MI double ingots, IE wires...). Variants that only share a tag (Remin yellow amethyst, IE duroplast plates) are recognised and set aside.
- **Lets you choose.** Click the item to keep for a form, triage every open duplicate with the keyboard (1-9), or rank mods once and let the priority decide. Every suggestion explains why it was made.
- **Rewrites the pack for you.** Alternatives leave the tags, recipes that output them are rewritten to the kept item (Create, Mekanism, Immersive Engineering, Modern Industrialization, Oritech, Create addons and more, as data), and items already in the world are converted when the game touches them (dropped, opened container, player login).
- **Shows everything first.** Preview lists every tag edit, recipe rewrite and generated recipe before anything is written. Apply writes one generated datapack and reloads.
- **Fills the gaps, on request.** Process rules say how a form is made (an ingot gives two rods in a metal press, nine ingots make a block) and generate the missing machine recipes by copying ones the pack already has. Missing forms (a netherite rod) can be created as new items.
- **Keeps you in control.** Pending changes can be discarded one by one, saved choices put back to default, the last apply reverted, and any of the last 20 applied states restored from the history.

## Getting started

1. Install Material Nexus (and optionally JEI or EMI) in your pack and open a singleplayer world.
2. Run `/materials` (or use the Nexus Terminal from the creative tab). Operators only (permission level 2).
3. On **Home**, use *Triage what is to decide* or *Unify all suggestions*, or rank mods under **Mod priority**.
4. Click **Preview and apply** at the bottom, read the preview, then **Apply**.

The other views: **Materials** (one material, all its forms and recipes), **Forms** (the whole pack as a grid, process rules per form), **Data** (edit the data files in game), **Presets** (ready-made pack-wide settings). `/materials report` writes a full analysis to `config/materialnexus/report.md`.

## Files

Everything Material Nexus decides lives in `config/materialnexus/` and is meant to be shipped with the pack:

| Path | What it is |
|---|---|
| `policies/global.json` | Pack-wide settings: `mod_priority`, `conversion_recipes`, `not_same`, `processes`, `almost_unified`, `exclude` |
| `policies/materials/*.json` | Your choice per material and form (`preferred_provider`, per-material priority or process) |
| `policies/data/` | Your in-game edits of the data files below (a datapack that wins over the mod's) |
| `items.json` | Items created for missing forms (registered at startup: restart after Apply) |
| `generated/` | The generated datapack. Build output, never edit it |
| `history.jsonl`, `history/` | What was applied and when, with a copy of the settings for each entry |

The rules Material Nexus works from are datapack data under `data/<namespace>/material_nexus/`, so a pack (or another mod) can add or override them: `forms` (tag folders, item name patterns, form relations), `process_templates`, `recipe_formats`, `presets`, `materials` (other names of a material).

## Multiplayer

Deciding is a singleplayer / LAN activity: on a dedicated server the screen is read-only. Author the pack in singleplayer and ship `config/materialnexus/` with it. If you created items, every client needs the same `items.json`.

## Compatibility

All integrations are optional and only active when the other mod is installed.

- **JEI / EMI:** alternatives are hidden from the item list once unified.
- **Almost Unified:** each domain (tags, recipe outputs, recipe removal, viewer hiding) is left to Almost Unified unless `global.json` gives it to Material Nexus, so the two never do the same job twice.
- **KubeJS:** scripts get a read-only `MaterialNexus` binding (`kept(item)`, `isAlternative(item)`, `canonical(material, form)`...). Scripts keep the last word over generated recipes.
- **Recipe formats** for many mods ship as data (`recipe_formats`); a mod without one is still unified through its tags, and its recipes are listed as not handled yet.

## Building

```bash
./gradlew build              # the mod jar, in build/libs
./gradlew test               # unit tests
./gradlew runGameTestServer  # in-game tests with the dev modpack (-Pvanilla without it, -Pau with Almost Unified, -Pkubejs with KubeJS)
./gradlew runClient
```

Design notes and decisions are in [`docs/`](docs), starting with `docs/00-PROJECT.md` and `docs/16-DECISIONS.md`.

## License

Code: [MIT](LICENSE). Assets (logo, textures): All Rights Reserved, see [LICENSE-ASSETS](LICENSE-ASSETS).
