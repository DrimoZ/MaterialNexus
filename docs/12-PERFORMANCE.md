# 12 — Performance

Material Nexus is deliberately not a tick-driven system.

## Budget

- zero global recipe scans per tick;
- zero world inventory scans;
- zero persistent item history;
- zero periodic S→C graph synchronization;
- one immutable snapshot lookup on gameplay paths;
- analysis only on startup, reload or explicit preview.

## Reload strategy

Use indexed maps keyed by `MaterialId`, `FormId`, `ResourceLocation` and recipe family ID. Do not repeatedly reconstruct semantic keys in render/tick loops.

## Client

GUI searches operate on already indexed snapshot views or server-provided targeted pages. Large recipe families should be paginated/virtualized rather than rendered as thousands of widgets.
