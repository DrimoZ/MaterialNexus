# Commands and Permissions

| Command | Does | Who |
|---|---|---|
| `/materials` | opens the screen | permission level 2 (operators; in singleplayer, cheats on) |
| `/materials report` | writes the full analysis to `config/materialnexus/report.md` | permission level 2, or the server console |

The **Nexus Terminal** (creative *Tools & Utilities* tab, shown when "Operator Items" is on) opens the
screen on right click, with the same permission check.

## The report

`report.md` lists, for every material, each form with its items, which is kept and why, what is set
aside and why, and the conversions no recipe provides. It ends with **Possibly untagged forms**:
item names that look like a form of a known material but are in no tag, grouped by name shape, to
review and declare ([Datapack Guide](Datapack-Guide#forms)), then **Tags**: items missing their tag
(what **Add missing tags** adds), recipes asking for a material tag nothing is in, and recipes asking
for `c:` tags (Forge 1.20.1 uses `forge:`). It works on a dedicated server too.

## Server-side checks

Every request from the screen is checked by the server: permission, then the content (a choice must
name an item that really is a member of that material and form; a history entry must exist; data
edits are decoded before being written). The screen never streams state: it asks when it needs
something.
