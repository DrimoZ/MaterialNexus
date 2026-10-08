# FAQ

**Does it change my pack on its own?**
No. Nothing is unified until you decide and press Apply, after the preview. A fresh install only
analyses.

**My pack already unifies with KubeJS. Will it undo my scripts?**
No. It reads what your scripts decided and offers the same choices; Preview warns if it would keep
another item. Once those choices are applied, the unification lines of your scripts can be deleted
([Modpacks and Multiplayer](Modpacks-and-Multiplayer#a-pack-that-already-unifies-with-scripts)).

**Is it like Almost Unified?**
Both unify. Material Nexus is built around a screen and a preview: you see every duplicate, every
variant set aside and every recipe it would change before anything happens, and you can undo
anything. It also writes missing machine recipes and creates missing items. The two can run together
([Compatibility](Compatibility#almost-unified)).

**What happens to items players already have?**
They become the kept item when the game touches them: dropped, in a container a player opens, in a
player's inventory at login. Nothing scans the world.

**Can I undo?**
Yes, at every step: discard pending changes, put choices back to default, revert the last apply, or
restore any of the last 20 applied states from the history.

**Why is an item not unified with the others of its tag?**
Hover it: it was set aside, and the reason is given (named as a variant, belongs to another material,
several items from the same mod, marked as not the same, excluded). Click it to keep it anyway.

**Why is a recipe "not handled yet"?**
Its type has no recipe format. Add one ([Datapack Guide](Datapack-Guide#recipe-formats)), or let
in-world conversion handle its output.

**Does it work on a dedicated server?**
Yes, with the decisions made beforehand in singleplayer and shipped in `config/materialnexus/`. On the
server, the screen is read-only.

**Do players need it?**
Yes if you created items (and with the same `items.json`). Otherwise it is needed on the server, and
on clients for the screen, the tooltips and the hiding in JEI or EMI.

**Can I put it in my modpack?**
Yes. No permission needed, public or private, monetised or not. Credit is appreciated, never
required.

**Fabric?**
No.

**A mod's ingot has no `c:ingots/...` tag, so recipes refuse it. Do I need a script?**
No. Once Material Nexus recognises it (declare its name pattern in the Data view's **Untagged
forms** tab), turn on **Add missing tags** there and Apply: the item joins `c:ingots/<material>`
and `c:ingots`. Preview lists every tag added.

**One item is tagged as the wrong material (or not tagged at all). Can I fix just that item?**
Yes. Shift + right click it in its form to take it out of that form's tag; to add an item, type its
id and the form (`tin/ingot`) in the Data view's **Untagged forms** tab. Both apply like any change.
