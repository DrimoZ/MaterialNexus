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

## Updating the pack

After adding or removing mods, open the screen in singleplayer: new duplicates show as *to decide*,
choices naming items that disappeared are flagged as ignored. Decide, preview, apply, and ship the
folder again.

## Permissions

The screen and `/materials` need permission level 2 (operators). Every request is checked again by
the server; holding the Nexus Terminal grants nothing. See
[Commands and Permissions](Commands-and-Permissions).
