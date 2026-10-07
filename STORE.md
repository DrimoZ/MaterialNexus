# Store copy

Paste-ready text for the CurseForge and Modrinth project pages. Not documentation: this file sells
the mod, the README explains it. Every claim here is something the mod does today; check it against
the code and `CHANGELOG.md` before changing a number.

No game version is named on purpose: the page stays true across ports, and each uploaded file
carries its own versions.

Images are generated, not committed: `./gradlew runUiShots -Pstore` takes the captures by itself (the
dev world as a fresh pack sees it), then `java tools/Banners.java run-ui/screenshots run-ui/store-art`
makes the images. The paths below are local until the first upload: upload each image (850 px wide
at most) through the description editor and replace its path with the `media.forgecdn.net` URL.


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

![Material Nexus](run-ui/store-art/banner.png)

### One copper ingot, not five.

A big modpack ends up with the same material made by every mod: five copper ingots, three tin
plates, a dozen recipes that disagree about them. Material Nexus finds every one of them, lets you
decide which item is kept, then cleans the tags, rewrites the recipes and converts the items for
you. Nothing changes until you have seen the preview and pressed Apply.

![Home](run-ui/store-art/screen_home.png)

![Finds the duplicates](run-ui/store-art/header_find.png)

![Every material and form](run-ui/store-art/screen_matrix.png)

- **Every form of every material**, read from the convention tags (`c:ingots/copper`,
  `c:plates/tin`...) and from item names for the forms mods leave untagged: double ingots, wires,
  large plates.
- **Variants are set aside, not merged**: an item that only shares a tag (a yellow amethyst, a
  plastic plate tagged as plates) is recognised by its name and left alone, with the word that gave
  it away.
- **The whole pack as a grid**: materials against forms, what is to decide, unified or single.

![You choose](run-ui/store-art/header_choose.png)

![One material](run-ui/store-art/screen_material.png)

- **Click the item to keep.** Each suggestion says why it was made, and each item which mod it
  comes from and how many recipes make and use it.
- **Triage with the keyboard**: every open duplicate, one at a time, 1-9 to keep, shift for "not
  the same item".
- **Or rank the mods once**: the first mod in the list wins every duplicate you have not decided.

![Triage](run-ui/store-art/screen_triage.png)

![Mod priority](run-ui/store-art/screen_priority.png)

![Preview, then apply](run-ui/store-art/header_preview.png)

![Preview](run-ui/store-art/screen_preview.png)

- **Every change listed first**: tags cleaned, items converted, recipes rewritten or disabled,
  recipes not handled yet.
- **Apply writes one generated datapack** and reloads: the alternatives leave the tags, recipes
  that output them give the kept item instead, and items already in the world become the kept one
  when the game touches them (dropped, in an opened container, at login).
- **Recipes of many mods** are rewritten from data: Create and its addons, Mekanism, Immersive
  Engineering, Modern Industrialization, Oritech, Occultism, Silent Gear and more.

![Fills the gaps](run-ui/store-art/header_process.png)

![Process rules](run-ui/store-art/screen_process.png)

- **Process rules** say how a form is made for every material: an ingot gives two rods in the
  metal press, nine ingots make a block. Missing machine recipes are written by copying one the
  pack already has, and checked by the game before they are kept.
- **Missing forms can be created**: a netherite rod, a tin gear. The new item takes the
  material's colour.

![Items it can create](run-ui/store-art/items_created.png)

![Always reversible](run-ui/store-art/header_control.png)

![Pending changes](run-ui/store-art/screen_pending.png)

- **Discard** any pending change, a whole material, or everything.
- **Back to default**: one saved choice, a material, or the whole pack.
- **Revert the last apply**, or restore any of the last 20 applied states from the history.
- **Presets** for the usual setups (vanilla first, tech pack...), shown next to your settings.

![Presets](run-ui/store-art/screen_presets.png)

![For pack makers](run-ui/store-art/header_data.png)

![Data](run-ui/store-art/screen_data.png)

Everything is files in `config/materialnexus/`, made to ship with the pack. The rules themselves
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

[Source](https://github.com/DrimoZ/MaterialNexus) ·
[Report a bug](https://github.com/DrimoZ/MaterialNexus/issues)

<!-- End of the pasted description. -->


## Release checklist: 0.1.0

- [ ] `main` green: `./gradlew test`, `./gradlew runGameTestServer`, `./gradlew runGameTestServer -Pvanilla`
      (and `-Pau`, `-Pkubejs` when those parts changed).
- [ ] Project avatar: `src/main/resources/logo.png` (from `java tools/GenerateLogo.java`).
- [ ] Generate the art (see the top of this file), upload every image and put the URLs in place.
- [ ] Gallery: `screen_home.png`, `screen_material.png`, `screen_matrix.png`, `screen_triage.png`,
      `screen_preview.png`, `screen_process.png`.
- [ ] Paste the summary and the description.
- [ ] Upload the jar from `gradlew build` (`materialnexus-0.1.0+1.21.1.jar`), release type **Beta**,
      loader NeoForge, with the `## 0.1.0` section of `CHANGELOG.md`.
- [ ] Optional dependencies: JEI, EMI, Almost Unified, KubeJS.
- [ ] Tag `v0.1.0`, and add the CurseForge link to the README and to the links above.
- [ ] Report upstream: `docs/upstream/immersiveengineering-silentgear-reload.md`.
