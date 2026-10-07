# Store copy

Paste-ready text for the CurseForge and Modrinth project pages. Not documentation: this file sells
the mod, the README explains it. Every claim here is something the mod does today; check it against
the code and `CHANGELOG.md` before changing a number.

No game version is named on purpose: the page stays true across ports, and each uploaded file
carries its own versions.

Images are generated, not committed: `./gradlew runUiShots -Pstore` takes the captures by itself (the
dev world as a fresh pack sees it), then `java tools/Banners.java run-ui/screenshots run-ui/store-art`
makes the images. The URLs below are the 0.1.0 uploads; after regenerating, upload the new images
(850 px wide at most) through the description editor and replace the URLs that changed.


## Summary

> One line, 256 characters at most, shown under the name in every search result.

Five copper ingots, three tin plates? Choose which item each material keeps, from an in-game screen. Material Nexus cleans the tags, rewrites the recipes and converts the items, and shows you every change before it applies.

## Categories

Main: **Utility & QoL**. Additional: API and Library.

## Licence field

Custom. Point it at `LICENSE` in the repository: MIT for the code, all rights reserved for the
artwork with redistribution granted.

## Description

<!-- Everything below this line is pasted into the site's Markdown editor as-is. -->

![Material Nexus](https://media.forgecdn.net/attachments/2023/202/banner-png.png)

### One copper ingot, not five.

A big modpack ends up with the same material made by every mod: five copper ingots, three tin
plates, a dozen recipes that disagree about them. Material Nexus finds every one of them, lets you
decide which item is kept, then cleans the tags, rewrites the recipes and converts the items for
you. Nothing changes until you have seen the preview and pressed Apply.

![Home](https://media.forgecdn.net/attachments/2023/211/screen_home-png.png)

![Finds the duplicates](https://media.forgecdn.net/attachments/2023/206/header_find-png.png)

![Every material and form](https://media.forgecdn.net/attachments/2023/213/screen_matrix-png.png)

- **Every form of every material**, read from the convention tags (`c:ingots/copper`,
  `c:plates/tin`...) and from item names for the forms mods leave untagged: double ingots, wires,
  large plates.
- **Variants are set aside, not merged**: an item that only shares a tag (a yellow amethyst, a
  plastic plate tagged as plates) is recognised by its name and left alone, with the word that gave
  it away.
- **The whole pack as a grid**: materials against forms, what is to decide, unified or single.

![You choose](https://media.forgecdn.net/attachments/2023/203/header_choose-png.png)

![One material](https://media.forgecdn.net/attachments/2023/212/screen_material-png.png)

- **Click the item to keep.** Each suggestion says why it was made, and each item which mod it
  comes from and how many recipes make and use it.
- **Triage with the keyboard**: every open duplicate, one at a time, 1-9 to keep, shift for "not
  the same item".
- **Or rank the mods once**: the first mod in the list wins every duplicate you have not decided.

![Triage](https://media.forgecdn.net/attachments/2023/219/screen_triage-png.png)

![Mod priority](https://media.forgecdn.net/attachments/2023/217/screen_priority-png.png)

![Preview, then apply](https://media.forgecdn.net/attachments/2023/207/header_preview-png.png)

![Preview](https://media.forgecdn.net/attachments/2023/216/screen_preview-png.png)

- **Every change listed first**: tags cleaned, items converted, recipes rewritten or disabled,
  recipes not handled yet.
- **Apply writes one generated datapack** and reloads: the alternatives leave the material's tag,
  recipes that make or use them are rewritten to the kept item (a rewrite that duplicates another
  recipe is disabled instead), and items already in the world become the kept one when the game
  touches them (dropped, in an opened container, at login). Nothing scans the world.
- **Recipes it cannot rewrite are named**, never guessed: add a one-file recipe format and they are.
- **Recipes of many mods** are rewritten from data: Create and its addons, Mekanism, Immersive
  Engineering, Modern Industrialization, Oritech, Occultism, Silent Gear and more.

![Fills the gaps](https://media.forgecdn.net/attachments/2023/208/header_process-png.png)

![Process rules](https://media.forgecdn.net/attachments/2023/218/screen_process-png.png)

- **Process rules** say how a form is made for every material: an ingot gives two rods in the
  metal press, nine ingots make a block. Missing machine recipes are written by copying one the
  pack already has, and checked by the game before they are kept.
- **Missing forms can be created**: a netherite rod, a tin gear. The new item takes the
  material's colour.

![Items it can create](https://media.forgecdn.net/attachments/2023/209/items_created-png.png)

![Always reversible](https://media.forgecdn.net/attachments/2023/204/header_control-png.png)

![Pending changes](https://media.forgecdn.net/attachments/2023/214/screen_pending-png.png)

- **Discard** any pending change, a whole material, or everything.
- **Back to default**: one saved choice, a material, or the whole pack.
- **Revert the last apply**, or restore any of the last 20 applied states from the history.
- **Presets** for the usual setups (vanilla first, tech pack...), shown next to your settings.

![Presets](https://media.forgecdn.net/attachments/2023/215/screen_presets-png.png)

![For pack makers](https://media.forgecdn.net/attachments/2023/205/header_data-png.png)

![Data](https://media.forgecdn.net/attachments/2023/210/screen_data-png.png)

Everything is files in `config/materialnexus/`, made to ship with the pack; the
[wiki](https://github.com/DrimoZ/MaterialNexus/wiki) documents every field. The rules themselves
(which tags and names make a form, how a mod's recipes store their items, process templates,
presets) are datapack data, editable in game in the Data view. `/materials report` writes a full
analysis. KubeJS scripts get a read-only `MaterialNexus` binding to ask which item is kept.

### Compatibility

| | |
|---|---|
| Loader | NeoForge |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) *(optional)* | alternatives hidden once unified |
| [EMI](https://www.curseforge.com/minecraft/mc-mods/emi) *(optional)* | alternatives hidden once unified |
| [Almost Unified](https://www.curseforge.com/minecraft/mc-mods/almost-unified) *(optional)* | each job (tags, recipes, hiding) left to it unless you give it to Material Nexus: never done twice |
| [KubeJS](https://www.curseforge.com/minecraft/mc-mods/kubejs) *(optional)* | a read-only `MaterialNexus` binding; scripts keep the last word |

Needed on the server, and on clients for the screen, the tooltips and created items.

### FAQ

**Does it change my pack on its own?** No. Nothing is unified until you choose and press Apply,
after the preview.

**Can I undo?** Yes: discard pending changes, put choices back to default, revert the last apply,
or restore an earlier state from the history.

**Multiplayer?** Decide in singleplayer or LAN, then ship `config/materialnexus/` with the pack. On
a dedicated server the screen is read-only. If you created items, every client needs the same
`items.json`.

**Is it like Almost Unified?** Both unify. Material Nexus is built around a screen and a
preview: you see every duplicate, every variant set aside and every recipe it would change before
anything happens, and you can undo anything. It also writes missing machine recipes and creates
missing items. The two can run together, each doing its own part.

**Fabric?** No.

**Can I put it in my modpack?** Yes. No need to ask.

### Permissions

**Modpacks: yes.** No permission needed, no message required, public or private, monetised or not,
on any platform or launcher.

**Credit** is appreciated and never required.

**Forks and addons: yes**, under the MIT terms. Please do not publish a fork under the name
*Material Nexus*: the name is not covered by the licence.

**Assets** (textures, logo) are the one exception: redistribute them with the mod freely, but do
not lift them into another project.

### Links

[Wiki](https://github.com/DrimoZ/MaterialNexus/wiki) ·
[Source](https://github.com/DrimoZ/MaterialNexus) ·
[Report a bug](https://github.com/DrimoZ/MaterialNexus/issues)

<!-- End of the pasted description. -->


## Release checklist: 0.1.0

- [ ] `main` green: `./gradlew test`, `./gradlew runGameTestServer`, `./gradlew runGameTestServer -Pvanilla`
      (and `-Pau`, `-Pkubejs` when those parts changed).
- [ ] Project avatar: `src/main/resources/logo.png` (from `java tools/GenerateLogo.java`).
- [x] Generate the art (see the top of this file), upload every image and put the URLs in place.
- [ ] Gallery: `screen_home.png`, `screen_material.png`, `screen_matrix.png`, `screen_triage.png`,
      `screen_preview.png`, `screen_process.png`.
- [ ] Paste the summary and the description.
- [ ] Upload the jar from `gradlew build` (`build/libs`), release type **Beta**,
      loader NeoForge, with the `## 0.1.0` section of `CHANGELOG.md`.
- [ ] Optional dependencies: JEI, EMI, Almost Unified, KubeJS.
- [ ] Tag `v0.1.0`, and add the CurseForge link to the README and to the links above.
- [ ] Report upstream: `docs/upstream/immersiveengineering-silentgear-reload.md`.
