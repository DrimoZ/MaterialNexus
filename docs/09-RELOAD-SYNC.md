# 09 — Reload and synchronisation

## Apply

1. validate the change plan shown in Preview;
2. copy `policies/` to `policies.bak/`;
3. write the new policy;
4. regenerate `generated/` and its `manifest.json`;
5. trigger `/reload`.

## `/reload`

Vanilla reloads recipes and tags atomically, including the generated pack. After it completes, Material Nexus:

1. collects registry/data state;
2. reads the pre-MNX JSON of every file in `manifest.json` via `ResourceManager#getResourceStack` (ADR-010);
3. rebuilds the material graph;
4. analyzes recipes;
5. resolves policy;
6. builds a new read-only snapshot and swaps the reference;
7. notifies integrations;
8. invalidates targeted client queries.

## Failure behavior

If analysis fails, show diagnostics and generate nothing. Gameplay state is whatever vanilla loaded; the snapshot is only an index for the GUI (ADR-002).

## Network

The client requests targeted views: material list, material detail, recipe family, diagnostics. Every request is permission-checked server-side. Do not synchronize the entire recipe graph every tick or permanently mirror it to every player.
