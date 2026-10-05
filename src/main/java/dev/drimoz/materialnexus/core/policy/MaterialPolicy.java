package dev.drimoz.materialnexus.core.policy;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record MaterialPolicy(
        MaterialId material,
        Map<FormId, ResourceLocation> preferredProviders,
        boolean unifyTags,
        boolean allowRecipeDisabling,
        boolean allowRecipeGeneration) {
    public MaterialPolicy {
        preferredProviders = Map.copyOf(preferredProviders);
    }
}
