package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

/**
 * Read-only index of one analysis (ADR-002). Both maps are unmodifiable and sorted. {@code scripts} and
 * {@code scriptDecisions}: what scripts changed in that load and what it decides (MNX-076).
 */
public record ResolvedSnapshot(
        long generation,
        Instant builtAt,
        DiscoveredMaterials discovered,
        SortedMap<MaterialId, ResolvedMaterial> materials,
        ScriptChanges scripts,
        List<ScriptDecisions.Decision> scriptDecisions) {
    public ResolvedSnapshot {
        scriptDecisions = List.copyOf(scriptDecisions);
    }

    public ResolvedSnapshot(long generation, Instant builtAt, DiscoveredMaterials discovered, SortedMap<MaterialId, ResolvedMaterial> materials) {
        this(generation, builtAt, discovered, materials, ScriptChanges.NONE, List.of());
    }

    public static ResolvedSnapshot empty() {
        return new ResolvedSnapshot(0, Instant.EPOCH, DiscoveredMaterials.EMPTY, Collections.emptySortedMap());
    }

    /** The current resolution of a material/form, if discovered. */
    public java.util.Optional<ResolvedForm> form(dev.drimoz.materialnexus.core.domain.MaterialForm key) {
        return java.util.Optional.ofNullable(materials.get(key.material())).map(m -> m.forms().get(key.form()));
    }
}
