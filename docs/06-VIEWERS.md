# 06 — JEI / EMI

JEI and EMI are presentation integrations. They never become the source of truth for gameplay.

## Integration responsibilities

- hide disabled recipes where the viewer supports it;
- expose generated/modified recipes normally;
- refresh category information after reload where the API permits it;
- provide viewer-specific buttons only in their own adapter.

If the viewer cannot refresh a registry state safely, Material Nexus reports `restart_required` instead of pretending the UI is synchronized.

## JEI (MNX-013)

The server sends the applied alternatives once per data sync (login and each reload, `UnifiedItemsPayload`). The JEI plugin hides them and shows them again when a revert brings them back; a new JEI runtime (JEI restarts on reload) re-applies the list. Disabled recipes are not loaded at all, so JEI never shows them. The plugin class is only instantiated by JEI: nothing loads when JEI is absent (verified with `-Pvanilla`).

## EMI (MNX-014)

Separate adapter (`@EmiEntrypoint`), same list: on each EMI reload, stacks of applied alternatives are removed. NeoForge posts `OnDatapackSyncEvent` before tags and recipes are sent, so the list is on the client before EMI reloads. Compiled against EMI only (`compileOnly`); `./gradlew runClient -Pemi` adds it to the dev run.

## Recipes drawn by the viewer (MNX-088)

Material Nexus draws no recipe itself. `client/RecipeViewer` is what the screen asks of an installed viewer: the
layout of a loaded recipe, the viewer's name for its machine, and opening the viewer on an item. Each adapter puts
itself in `RecipeViewer.Slot.current`; with both installed, EMI keeps the slot. With no viewer, recipes are listed
without a drawing.

- **JEI**: the layout comes from the category that registered the recipe (`createRecipeLayoutDrawable`).
- **EMI**: the widgets EMI builds for the recipe, rendered at the asked position. A mod with only a JEI plugin is
  found under EMI's id for JEI recipes, `jei:/<namespace>/<path>`. Slot tooltips are not shown.

Where it is used: the inspector of the Recipes tab, a box next to the pointer when a recipe row is hovered, and the
same box on a route of "How it's made", which shows one recipe of the pack following that route (the server sends
one recipe id per found route) and says it is an example. Machine names in both tabs are the viewer's.

What only Material Nexus shows: for a recipe whose output changes, the replaced item struck through next to the
kept one. A disabled recipe is no longer loaded, so no viewer can draw it; its row is the only place it appears.
