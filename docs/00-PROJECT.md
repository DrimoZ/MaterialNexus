# 00 — Project

## Positioning

Material Nexus is a **visual, data-driven manager for the materials and recipes of a modpack**.

It does not try to replace KubeJS. It provides a specialized, safe and explainable layer for the very concrete problem of duplicate materials and recipes.

## Target user

- modpack creator;
- maintainer of a modded server;
- author who wants to change a pack without writing dozens of JSON files/scripts;
- advanced player/OP diagnosing a material incompatibility.

## UX promise

> "I pick Copper once. I see what the pack contains, what is canonical, which recipes exist and what will change."

## Out of scope

- persistent inventory management;
- item history;
- chest scanning;
- logistics automation;
- automatic balancing;
- general-purpose scripting engine;
- worldgen;
- full JEI/EMI replacement.

## Main cycle

`Discover → Normalize → Analyze → Resolve Policy → Preview → Apply (generate datapack + /reload) → Snapshot`
