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

## Other mods failing during a live apply

Apply reloads data while the world runs. Some mods cache state across reloads and fail in their own reload listener when unified tags change live. Known case: Immersive Engineering 12.4.2 arc recycling throws `ArrayIndexOutOfBoundsException` in `OnDatapackSyncEvent` when metal tags lose members (reproduced: tags-only transition fails, same pack present at startup reloads fine).

The policy and pack are already written and the data swapped when that happens, so Material Nexus reports it as "applied, another mod failed while reloading", keeps the GUI open on the new state and clears pending choices. Restarting the world gives a clean state. Pending choices are only dropped once the server confirms the apply.
