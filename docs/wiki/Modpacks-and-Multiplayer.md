# Modpacks and Multiplayer

## Who decides

Deciding is done in **singleplayer** or on a world **opened to LAN**, by an operator. On a dedicated
server the screen opens **read-only**: you can browse materials and the preview, not apply. Applying
reloads the server's data and rewrites files, which is a pack-building step, not a live-server one.

## What to ship

Ship `config/materialnexus/` with the pack, at least:

- `policies/`: your decisions;
- `generated/`: the datapack the pack plays with;
- `items.json`, if you created items.

The history and the report are not needed. `created_items/` is rebuilt at startup.

## Clients

Material Nexus is needed on the server. On clients it adds the screen, the tooltips and the hiding of
alternatives in JEI or EMI. If you **created items**, it is required on clients too, with the **same
`items.json`**: the items are registered at startup on both sides, and a mismatch stops players from
joining.

## A pack that already unifies with scripts

Material Nexus can be added to a pack whose KubeJS scripts already unify by hand. It reads what the
scripts changed when the game loads: tag entries they removed or added, and recipes they replaced,
removed or created. From that it works out which item the scripts keep for each material and form.
Nothing is applied on its own:

1. Install it, scripts untouched. The Home view says how many forms your scripts already decide, and
   how many differ from what Material Nexus would keep.
2. Open **Scripts**, and **Keep all** (or **Keep** row by row). Each one is a pending choice, like any
   other. Preview warns about every form where Material Nexus would still keep another item than
   your scripts.
3. Apply. Those forms show as **recorded**: their decision now lives in your settings.
4. Delete the unification lines of your scripts (each row says where: **seen in unify.js:12**,
   when the item id is written in full) and `/reload`. Nothing changes in game: Material
   Nexus now does it.

Recipes your scripts create (not unification) stay in your scripts; `/materials report` lists them
with their file and line. Without KubeJS, tag changes are still read; recipe changes are not.

## Updating the pack

After adding or removing mods, open the screen in singleplayer: new duplicates show as *to decide*,
choices naming items that disappeared are flagged as ignored. Decide, preview, apply, and ship the
folder again.

## Permissions

The screen and `/materials` need permission level 2 (operators). Every request is checked again by
the server; holding the Nexus Terminal grants nothing. See
[Commands and Permissions](Commands-and-Permissions).
