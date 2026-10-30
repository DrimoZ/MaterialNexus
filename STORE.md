# Store copy

Paste-ready text for the CurseForge and Modrinth project pages. Not documentation: this file sells
the mod, the README explains it. Every claim here is something the mod does today; check it against
the code and `CHANGELOG.md` before changing a number.

No game version is named on purpose: the page stays true across ports, and each uploaded file
carries its own versions.

Images are generated and committed on `main` (`docs/store-art`), which the description links from GitHub
(`raw.githubusercontent.com`), never from the description editor: an image uploaded there joins the
CurseForge gallery. One project page serves every port, so the art comes from `main`.


## Summary

> One line, 256 characters at most, shown under the name in every search result.

One copper ingot, not five. Unify duplicate ingots, ores and dusts in your modpack: pick the one to keep from an in-game screen, and tags, recipes and items follow. No scripts, full preview before anything changes.

## Categories

Main: **Utility & QoL**. Additional: API and Library.

## Licence field

Custom. Point it at `LICENSE` in the repository: MIT for the code, all rights reserved for the
artwork with redistribution granted.

## Description

<!-- Everything below this line is pasted into the site's Markdown editor as-is. -->

![Material Nexus](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/banner.png)

### One copper ingot, not five.

A big modpack ends up with the same material made by every mod: five copper ingots, three tin
plates, a dozen recipes that disagree about them. Material Nexus finds every one of them, lets you
decide which item is kept, then cleans the tags, rewrites the recipes and converts the items for
you. Nothing changes until you have seen the preview and pressed Apply.

**Start here:** open a singleplayer world with cheats allowed and run **`/materials`**, or right
click with the **Nexus Terminal** (creative *Tools & Utilities* tab, with "Operator Items" on). It
needs operator rights (permission level 2).

[Wiki](https://github.com/DrimoZ/MaterialNexus/wiki) · [Getting Started](https://github.com/DrimoZ/MaterialNexus/wiki/Getting-Started) ·
[Commands](https://github.com/DrimoZ/MaterialNexus/wiki/Commands-and-Permissions) ·
[Report a bug](https://github.com/DrimoZ/MaterialNexus/issues)

![Overview](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_home.png)

![Finds the duplicates](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_find.png)

![Every material and form](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_matrix.png)

- **Every form of every material**, read from the convention tags (`forge:ingots/copper`,
  `forge:plates/tin`...) and from item names for the forms mods leave untagged: double ingots, wires,
  large plates.
- **Variants are left alone, not merged**: an item that only shares a tag (a yellow amethyst, a
  plastic plate tagged as plates) is recognised by its name and left alone, with the word that gave
  it away.
- **The whole pack as a grid**: materials against forms, what is to decide, unified or single.

![You choose](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_choose.png)

![One material](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_material.png)

![Every item, with its reasons](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_inspector.png)

- **Click the item to keep, or use its card**: every item with its mod, how many recipes make and
  use it, and why it is suggested or left alone. Every action is a button, never a hidden gesture.
- **Sort with the keyboard**: every open duplicate, one at a time, 1-9 to keep, shift for "not
  the same item".
- **Or rank the mods once**: the first mod in the list wins every duplicate you have not decided.

![Sort duplicates](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_triage.png)

![Mod priority](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_priority.png)

![Review, then apply](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_preview.png)

![Review and apply](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_preview.png)

- **Every change listed first**: tags cleaned, items converted, recipes rewritten or disabled,
  recipes not handled yet.
- **Apply writes one generated datapack** and reloads: the alternatives leave the material's tag,
  recipes that make or use them are rewritten to the kept item (a rewrite that duplicates another
  recipe is disabled instead), and items already in the world become the kept one when the game
  touches them (dropped, in an opened container, at login). Nothing scans the world.
- **Tags fixed on request**: an item a mod forgot to tag gets its convention tag, and any item can be
  put in or taken out of a form's tag, so recipes asking for the tag accept the right items.
- **Recipes of any mod are rewritten**, not only the ones it was told about: the usual keys tell inputs
  from outputs, the game must agree, and an input asks for the form's tag (`forge:plates/chromium`) when
  that tag holds only that material, the kept item otherwise. Conditional recipes are read through.
- **Formats ship for thirty mods** (Create and its addons, Mekanism, Immersive Engineering, Thermal,
  Tinkers' Construct, Tensura, Energized Power, Extended AE, Ars Nouveau, Goety, Aether, Silent Gear,
  Electrodynamics and more) and only act where the mod is installed. A recipe it still cannot read is
  named, never guessed: add a one-file recipe format, or `skip` the type.
- **Recipes drawn by JEI or EMI** inside the screen, grouped by what applying does to them; hover a
  recipe or a machine route to see it.
- **Remove a recipe** with the bin on its row: it waits for the review like every choice, and one
  click puts it back.

![Recipes](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_recipes.png)

![Fills the gaps](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_process.png)

![Process rules](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_process.png)

- **Process rules** say how a form is made for every material: an ingot gives two rods in the
  metal press, nine ingots make a block. Missing machine recipes are written by copying one the
  pack already has, and checked by the game before they are kept.
- **Missing forms can be created**: a netherite rod, a tin gear. The new item takes the
  material's colour.

![Items it can create](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/items_created.png)

![Always reversible](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_control.png)

![Your changes](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_pending.png)

- **Discard** any change not applied yet, a whole material, or everything.
- **Back to default**: one saved choice, a material, or the whole pack.
- **Revert the last apply**, or restore any of the last 20 applied states from the history.
- **Presets** for the usual setups (vanilla first, tech pack...), shown next to your settings.

![Presets](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_presets.png)

![For pack makers](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/header_data.png)

![Rule files](https://raw.githubusercontent.com/DrimoZ/MaterialNexus/main/docs/store-art/screen_data.png)

Everything is files in `config/materialnexus/`, made to ship with the pack; the
[wiki](https://github.com/DrimoZ/MaterialNexus/wiki) documents every field. The rules themselves
(which tags and names make a form, how a mod's recipes store their items, process templates,
presets) are datapack data, editable in game in Rule files. `/materials report` writes a full
analysis. KubeJS scripts get a read-only `MaterialNexus` binding to ask which item is kept, and a pack
that already unifies with scripts sees what they decided, offered as the same choices, with the script
lines to delete once applied. Your own datapacks are read too, never changed: the screen names the one
that replaces or switches off a recipe or edits a form's tag, and the review warns before undoing it.

### Compatibility

| | |
|---|---|
| Loader | Forge |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) *(optional)* | alternatives hidden once unified |
| [EMI](https://www.curseforge.com/minecraft/mc-mods/emi) *(optional)* | alternatives hidden once unified |
| [Almost Unified](https://www.curseforge.com/minecraft/mc-mods/almost-unified) *(optional)* | each job (tags, recipes, hiding) left to it unless you give it to Material Nexus: never done twice |
| [KubeJS](https://www.curseforge.com/minecraft/mc-mods/kubejs) *(optional)* | a read-only `MaterialNexus` binding; what scripts already unify is offered as choices; scripts keep the last word |

Needed on the server, and on clients for the screen, the tooltips and created items.

### FAQ

**Does it change my pack on its own?** No. Nothing is unified until you choose and press Apply,
after the preview.

**Can I undo?** Yes: discard what is not applied yet, put choices back to default, revert the last apply,
or restore an earlier state from the history.

**Multiplayer?** Decide in singleplayer or LAN, then ship `config/materialnexus/` with the pack. On
a dedicated server the screen is read-only. If you created items, every client needs the same
`items.json`.

**Is it like Almost Unified?** Both unify. Material Nexus is built around a screen and a
preview: you see every duplicate, every variant left alone and every recipe it would change before
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


## Release checklist: 0.5.0

- [ ] `forge-1.20.1` green: `./gradlew test`, `./gradlew runGameTestServer`, `./gradlew runGameTestServer -Pvanilla`
      (and `-Pau`, `-Pkubejs` when those parts changed).
- [x] Project avatar: `src/main/resources/logo.png` (from `java tools/GenerateLogo.java`), unchanged since 0.2.0.
- [x] Art: generated and pushed on `main` (MNX-122); the description loads it from GitHub.
- [x] Wiki published from `main`'s `docs/wiki` (MNX-122).
- [ ] Gallery (850 px wide at most): `screen_material.png`, `screen_inspector.png`, `screen_matrix.png`,
      `screen_priority.png`, `screen_preview.png`, `screen_process.png`. The wider ones (`screen_home.png`,
      `screen_triage.png`, `screen_recipes.png`...) are for the description only.
- [ ] Paste the description (new since 0.4.0: recipes of any mod rewritten, formats for thirty mods).
- [ ] Upload the jar from `gradlew build` (`build/libs`), release type **Beta**,
      loader Forge, with the `## 0.5.0` section of `CHANGELOG.md`.
- [ ] Optional dependencies: JEI, EMI, Almost Unified, KubeJS.
- [ ] Tag `v0.5.0+1.20.1`.
