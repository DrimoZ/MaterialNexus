package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record ResolvedMaterial(MaterialId material, Map<FormId, ResourceLocation> canonicalByForm) {
    public ResolvedMaterial {
        canonicalByForm = Map.copyOf(canonicalByForm);
    }
}
