# Material Nexus

![Material Nexus](src/main/resources/logo.png)

Material unification for modpack makers, from an in-game screen. A pack ends up with the same
material made by every mod: five copper ingots, three tin plates, recipes that each want a different
one. Material Nexus finds them, lets you decide which item each material keeps, and makes the pack
agree: tags, recipes, the items already in the world, and the recipe viewer. Nothing changes until
you have read the preview and pressed Apply.

**Documentation:** [the wiki](https://github.com/DrimoZ/MaterialNexus/wiki)

## What it does

- **Finds every material form** in the pack, from the convention tags (`c:ingots/copper`,
  `c:plates/tin`) and from item names for the forms mods leave untagged (double ingots, wires, large
  plates).
- **Tells duplicates from variants.** Two copper ingots from two mods are duplicates. An item that
  only shares a tag (a yellow amethyst tagged as amethyst, a plastic plate tagged as plates, several
  items from the same mod) is set aside, with the reason.
- **Lets you choose**, three ways: click the item to keep, triage every open duplicate with the
  keyboard, or rank mods once and let the priority decide what you have not.
- **Previews every change**, then writes one generated datapack: alternatives leave the material's
  tag, recipes making or using them are rewritten to the kept item, duplicates that result are
  disabled, items in the world are converted when the game touches them, JEI and EMI hide the
  alternatives.
- **Fills the gaps, on request.** A process rule says how a form is made for every material (an
  ingot gives two rods in the metal press) and writes the missing machine recipes by copying one the
  pack already has. A form a material lacks (a netherite rod) can be created as a new item.
- **Fixes tags, on request.** An item a mod forgot to tag gets its convention tag, and any item can
  be put in or taken out of a form's tag (Shift + right click), so recipes asking for the tag accept
  the right items.
- **Undoes anything**: discard pending changes one by one, put saved choices back to default,
  revert the last apply, or restore any of the last 20 applied states.

## Using it

Open a singleplayer world with the pack, run `/materials` (operators), decide, then **Preview and
apply**. Ship `config/materialnexus/` with the pack; on a dedicated server the screen is read-only.

| | |
|---|---|
| [Getting Started](https://github.com/DrimoZ/MaterialNexus/wiki/Getting-Started) | from install to the first Apply |
| [The Screen](https://github.com/DrimoZ/MaterialNexus/wiki/The-Screen) | every view, button and key |
| [Choosing Items](https://github.com/DrimoZ/MaterialNexus/wiki/Choosing-Items) | suggestions, priorities, variants |
| [Applying Changes](https://github.com/DrimoZ/MaterialNexus/wiki/Applying-Changes) | what Apply writes, and undoing |
| [Configuration Files](https://github.com/DrimoZ/MaterialNexus/wiki/Configuration-Files) | everything in `config/materialnexus/` |
| [Datapack Guide](https://github.com/DrimoZ/MaterialNexus/wiki/Datapack-Guide) | forms, recipe formats, process templates, presets |
| [Compatibility](https://github.com/DrimoZ/MaterialNexus/wiki/Compatibility) | JEI, EMI, Almost Unified, KubeJS |

## Compatibility

Every integration is optional.

- **JEI, EMI**: alternatives hidden once unified.
- **Almost Unified**: each domain (tags, recipe rewriting, recipe removal, viewer hiding) is left to
  it unless `global.json` gives it to Material Nexus, so nothing is done twice.
- **KubeJS**: a read-only `MaterialNexus` binding (`kept(item)`, `isAlternative(item)`,
  `canonical(material, form)`...); scripts keep the last word. What scripts already unify is read at
  load and offered as the same choices, with the script lines that make them, so a pack can move its
  unification out of its scripts
  ([details](https://github.com/DrimoZ/MaterialNexus/wiki/Modpacks-and-Multiplayer#a-pack-that-already-unifies-with-scripts)).
- **Recipes rewritten** for vanilla, Create and its addons, Mekanism, Immersive Engineering, Modern
  Industrialization, Oritech, Occultism, Silent Gear, Ex Deorum and more, through recipe formats that
  are data: any mod can be added with one JSON file.

## Building

```bash
./gradlew build              # the jar, in build/libs
./gradlew test               # unit tests
./gradlew runGameTestServer  # in-game tests on the dev modpack (-Pvanilla without it, -Pau with Almost Unified, -Pkubejs with KubeJS)
./gradlew runClient
./gradlew runUiShots -Pstore # the store captures; then java tools/Banners.java run-ui/screenshots run-ui/store-art
```

| | |
|---|---|
| `docs/` | design notes and decisions, from `00-PROJECT.md` to `16-DECISIONS.md` |
| `docs/wiki/` | the source of the wiki, published as described in its README |
| `STORE.md` | the CurseForge and Modrinth page |
| `tools/` | the logo and store art generators |

## License

Code: [MIT](LICENSE). Assets (logo, textures): all rights reserved, redistributed with the mod; see
[LICENSE-ASSETS](LICENSE-ASSETS).
