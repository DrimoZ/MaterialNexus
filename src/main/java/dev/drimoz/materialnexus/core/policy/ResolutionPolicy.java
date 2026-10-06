package dev.drimoz.materialnexus.core.policy;

import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Pack-author intent for canonical selection, one field per precedence level (ADR-006).
 * Mod priorities are namespaces, strongest first.
 */
public record ResolutionPolicy(
        List<String> globalModPriority,
        Map<MaterialId, List<String>> materialModPriority,
        Map<MaterialForm, List<String>> formModPriority,
        Map<MaterialForm, ResourceLocation> explicitProviders) {
    public static final ResolutionPolicy NONE = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of());

    public ResolutionPolicy {
        globalModPriority = List.copyOf(globalModPriority);
        materialModPriority = Map.copyOf(materialModPriority);
        formModPriority = Map.copyOf(formModPriority);
        explicitProviders = Map.copyOf(explicitProviders);
    }
}
