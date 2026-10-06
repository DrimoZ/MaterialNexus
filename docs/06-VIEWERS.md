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
