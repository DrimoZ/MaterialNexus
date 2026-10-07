# Choosing Items

## Materials and forms

A **material** is copper, tin, diamond, steel. A **form** is what it is shaped as: ingot, nugget,
dust, plate, rod, gear, wire, block, ore, raw... Material Nexus reads them from the convention tags:
`forge:ingots/copper` says "these items are copper ingots". Forms mods leave untagged (Modern
Industrialization's double ingots and wires) are found by item name patterns, which are data
([Datapack Guide](Datapack-Guide#forms)).

A gem is the ingot of non-metals (diamond, quartz, coal): `forge:gems/<material>`.

## Duplicates, variants and other materials

A tag means "usable as", not "the same item". So within a form, Material Nexus sorts the candidates:

- **Duplicates**: the same thing from different mods (copper ingots from Mekanism and Immersive
  Engineering). One is kept, the others are **alternatives**.
- **Set aside**, never unified unless you pick one:
  - several items **from the same mod** (it made them different on purpose);
  - an item that **belongs to a more specific material** (`forge:ingots/steel` and `forge:ingots/hot_steel`);
  - an item **named as a variant**: its id has a word left once the material and the form words are
    removed (`remin:yellow_amethyst` tagged as amethyst, `immersiveengineering:plate_duroplast`
    tagged as plastic plates, `create:crushed_raw_iron` tagged as raw iron);
  - an item **named after another known material** (`minecraft:quartz` tagged as milky quartz);
  - an item you **marked as not the same** (right click, or Shift + number in triage);
  - a material or form **excluded** in `global.json`.

  Words close to the material (golden for gold, aluminium for aluminum) and the words of the
  material's vanilla items do not count as variant words, and a vanilla item is never a variant.
  The name rule only applies when some candidate is plainly named.

Hover an item to see why it was set aside.

## What decides the kept item

From strongest to weakest; the first that applies wins:

| Level | Set by | Status |
|---|---|---|
| Your choice for that form | clicking an item, triage, Unify | Unified (chosen) |
| Form priority | `mod_priority` of a form in a material file | Unified (by priority) |
| Material priority | `mod_priority` of a material file | Unified (by priority) |
| Global mod priority | the Mod priority list on Home | Unified (by priority) |
| Default suggestion | nothing | To decide |

The **default suggestion** is the vanilla item when there is one, else the item with the strongest
evidence (a tag beats a name pattern), then the alphabetically first id. A suggestion is only a
proposal: it unifies nothing until it becomes a choice (accept it, triage it, or Unify).

A choice naming an item that is no longer a member of that form (a mod removed, a tag changed) is
ignored, and the row says so; **↺** removes it.

## Mod priority

![Mod priority](images/screen_priority.png)

The list on Home: the first mod in it wins every duplicate it is part of that you have not decided
yourself. **+** ranks a mod, **▲ ▼** move it, **×** unranks it, **↺ Reset** empties the list. Every
edit is pending until Apply.

The same list can be set per material or per form, in a material file
([Configuration Files](Configuration-Files#a-material-file)).

## Not the same

Right click an item (or Shift + its number in triage) to say it is not interchangeable with the
others in its tag: it is set aside, with "marked as not the same". It is stored in `not_same` of
`global.json`. Picking it explicitly still works.

## Presets

![Presets](images/screen_presets.png)

A preset is a ready-made set of pack-wide settings: which mod wins duplicates, which forms get
conversion recipes. Shipped:

| Preset | Mod priority | Conversion recipes |
|---|---|---|
| Vanilla first | Minecraft | none |
| Create first | Create, Minecraft | ingot, nugget, plate |
| Tech pack | Mekanism, Modern Industrialization, Immersive Engineering, Create, Minecraft | ingot, nugget, dust, plate |
| Maximum unification | Minecraft, Mekanism, Modern Industrialization, Immersive Engineering, Create | ingot, nugget, block, dust, plate, gear, rod, wire, raw, raw block |
| Minimal changes | none | none |

**Use this preset** only fills those settings as pending changes, shown in Preview. Your choices per
form are kept and still win. Packs add their own presets as data
([Datapack Guide](Datapack-Guide#presets)).

## Excluding

`"exclude": ["wood", "steel/rod"]` in `global.json`: a whole material, or one form of it, is never
unified. Its items are listed as excluded.
