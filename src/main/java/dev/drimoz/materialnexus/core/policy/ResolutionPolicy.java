package dev.drimoz.materialnexus.core.policy;

import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pack-author intent for canonical selection, one field per precedence level (ADR-006).
 * Mod priorities are namespaces, strongest first. Excluded materials/forms are never unified.
 */
public record ResolutionPolicy(
        List<String> globalModPriority,
        Map<MaterialId, List<String>> materialModPriority,
        Map<MaterialForm, List<String>> formModPriority,
        Map<MaterialForm, ResourceLocation> explicitProviders,
        Set<MaterialId> excludedMaterials,
        Set<MaterialForm> excludedForms) {
    public static final ResolutionPolicy NONE = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of());

    public ResolutionPolicy {
        globalModPriority = List.copyOf(globalModPriority);
        materialModPriority = Map.copyOf(materialModPriority);
        formModPriority = Map.copyOf(formModPriority);
        explicitProviders = Map.copyOf(explicitProviders);
        excludedMaterials = Set.copyOf(excludedMaterials);
        excludedForms = Set.copyOf(excludedForms);
    }

    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, Set.of(), Set.of());
    }

    public boolean isExcluded(MaterialForm key) {
        return excludedMaterials.contains(key.material()) || excludedForms.contains(key);
    }
}
