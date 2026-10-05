# 06 — JEI / EMI

JEI and EMI are presentation integrations. They never become the source of truth for gameplay.

## Integration responsibilities

- hide disabled recipes where the viewer supports it;
- expose generated/modified recipes normally;
- refresh category information after reload where the API permits it;
- provide viewer-specific buttons only in their own adapter.

If the viewer cannot refresh a registry state safely, Material Nexus reports `restart_required` instead of pretending the UI is synchronized.
