# Troubleshooting

**The screen does not open.**
You need permission level 2: be an operator, or allow cheats in singleplayer.

**The screen opens read-only.**
You are on a dedicated server. Decide in singleplayer or LAN, then ship `config/materialnexus/`.

**An item I expect is not found.**
It has no convention tag for its form. Check `/materials report`, "Possibly untagged forms", or the
Data view's **Untagged forms** tab, and declare its name pattern
([Datapack Guide](Datapack-Guide#forms)).

**Two different items are unified together.**
Right click the wrong one: **not the same**. If a whole material should never be unified, add it to
`exclude` in `global.json`.

**A recipe still gives the old item.**
Look for it in the material's **Recipes** tab. *Not handled yet*: its type has no recipe format. If
it comes from KubeJS, the script runs after Material Nexus; use `MaterialNexus.kept` there. If it is
listed as rewritten, check that the apply finished (the chat says so).

**"Changes applied, but another mod failed while reloading".**
Another mod threw during the reload (seen with Immersive Engineering and Silent Gear together). The
changes are applied and the tags and recipes were resent; if that mod misbehaves, restart the world.

**A created item does not appear.**
Restart the game: items are registered at startup. On a server, the clients need the same
`items.json`.

**A choice shows as ignored.**
The item it names is no longer a member of that form (a mod was removed, or a tag changed). Pick
another item, or put the form back to default with **↺**.

**The analysis failed after a hand edit.**
The log names the file and the field. Fix it, then `/reload`.

**Something else.**
[Report it](https://github.com/DrimoZ/MaterialNexus/issues) with your `latest.log` and
`config/materialnexus/report.md`.
