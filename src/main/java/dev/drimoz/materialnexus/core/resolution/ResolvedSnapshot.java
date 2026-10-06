package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.MaterialId;

import java.time.Instant;
import java.util.Collections;
import java.util.SortedMap;

/** Read-only index of one analysis (ADR-002). Both maps are unmodifiable and sorted. */
public record ResolvedSnapshot(
        long generation,
        Instant builtAt,
        DiscoveredMaterials discovered,
        SortedMap<MaterialId, ResolvedMaterial> materials) {
    public static ResolvedSnapshot empty() {
        return new ResolvedSnapshot(0, Instant.EPOCH, DiscoveredMaterials.EMPTY, Collections.emptySortedMap());
    }
}
