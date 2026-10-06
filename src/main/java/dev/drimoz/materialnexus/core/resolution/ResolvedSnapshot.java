package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.MaterialId;

import java.time.Instant;
import java.util.Map;

public record ResolvedSnapshot(
        long generation,
        Instant builtAt,
        DiscoveredMaterials discovered,
        Map<MaterialId, ResolvedMaterial> materials) {
    public ResolvedSnapshot {
        materials = Map.copyOf(materials);
    }

    public static ResolvedSnapshot empty() {
        return new ResolvedSnapshot(0, Instant.EPOCH, DiscoveredMaterials.EMPTY, Map.of());
    }
}
