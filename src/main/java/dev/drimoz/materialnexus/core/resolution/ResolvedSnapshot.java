package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.domain.MaterialId;

import java.time.Instant;
import java.util.Map;

public record ResolvedSnapshot(
        long generation,
        Instant builtAt,
        Map<MaterialId, ResolvedMaterial> materials) {
    public ResolvedSnapshot {
        materials = Map.copyOf(materials);
    }

    public static ResolvedSnapshot empty() {
        return new ResolvedSnapshot(0, Instant.EPOCH, Map.of());
    }
}
