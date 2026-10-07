# Compatibility

Every integration is optional, and only active when the other mod is installed.

## JEI and EMI

Once applied, alternatives are hidden from the item list, so players only see the kept items.
Recipes shown are the rewritten ones.

## Almost Unified

Almost Unified unifies by default. With it installed, Material Nexus only acts in the domains you give
it, so the two never do the same job twice:

```json
"almost_unified": { "tags": "mnx", "output_rewrite": "mnx", "recipe_disable": "mnx", "viewer_hiding": "au" }
```

| Domain | |
|---|---|
| `tags` | removing alternatives from the convention tags |
| `output_rewrite` | rewriting recipes to the kept item |
| `recipe_disable` | disabling recipes that became duplicates |
| `viewer_hiding` | hiding alternatives in JEI or EMI |

A domain not listed is left to Almost Unified, and Preview says so. Conversion of items in the world,
conversion recipes, process rules and created items are not Almost Unified domains and stay with
Material Nexus. Note that Almost Unified unifies recipe outputs as the game loads, so Material Nexus
finds fewer recipes to rewrite even in domains given to it.

## KubeJS

Scripts get a read-only `MaterialNexus` binding:

| Call | Answer |
|---|---|
| `MaterialNexus.kept(item)` | the item kept in place of `item`, or `item` itself |
| `MaterialNexus.isAlternative(item)` | whether `item` is converted to another one |
| `MaterialNexus.conversions()` | every alternative id and its kept id |
| `MaterialNexus.canonical(material, form)` | the kept item id, or null |
| `MaterialNexus.alternatives(material, form)` | the other items of that form |

The first three follow the applied pack and are right inside recipe events; the last two follow the
settings as resolved at the last reload.

```js
ServerEvents.recipes(event => {
  event.shapeless(MaterialNexus.kept('thermal:tin_ingot'), ['9x #c:nuggets/tin'])
})
```

**Scripts have the last word.** KubeJS edits recipes and tags after datapacks, Material Nexus's
included: a script that changes a recipe applies on top of what Material Nexus generated. A recipe
created by a script has no file to rewrite and is listed as "not handled yet". A script matching an
alternative by item id stops matching once that recipe is rewritten; match by tag, or use
`MaterialNexus.kept`.

## Recipe formats

Recipes are rewritten for the recipe types a format describes. Shipped formats:

| Mod | |
|---|---|
| Minecraft | crafting, smelting, blasting, smoking, campfire, stonecutting, smithing |
| Create | every processing type |
| Create Crafts & Additions, Create: New Age, Create Metallurgy | their machines |
| Mekanism | crushing, enriching, sawing, injecting, combining, infusing, purifying, crystallizing, compressing, reaction... |
| Immersive Engineering, Immersive Petroleum | their machines |
| Modern Industrialization | its machines |
| Oritech, Occultism, Silent Gear, Ex Deorum, Extreme Reactors | their machines |

Any other mod: add a format ([Datapack Guide](Datapack-Guide#recipe-formats)). Its items are still
unified through tags and in the world in the meantime.

## Forms and tags

- Modern Industrialization's untagged forms (double ingots, large and curved plates, bolts, rings,
  blades, rotors, drill heads, wires, fine wires), Oritech's small dusts and Realistic Minerals' ores
  are found by shipped name patterns.
- Coal and charcoal are tagged as gems (`c:gems/coal`, `c:gems/charcoal`), as vanilla leaves them untagged.
