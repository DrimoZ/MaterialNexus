package dev.drimoz.materialnexus.core.policy;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pack-author intent: canonical selection, one field per precedence level (ADR-006), exclusions
 * (ADR-014), the forms for which unification also generates 1:1 conversion recipes, and the
 * arbitration with Almost Unified (ADR-012), and process rules (MNX-036). Mod priorities are namespaces, strongest first.
 */
public record ResolutionPolicy(
        List<String> globalModPriority,
        Map<MaterialId, List<String>> materialModPriority,
        Map<MaterialForm, List<String>> formModPriority,
        Map<MaterialForm, ResourceLocation> explicitProviders,
        Set<MaterialId> excludedMaterials,
        Set<MaterialForm> excludedForms,
        Set<FormId> conversionRecipeForms,
        AlmostUnified almostUnified,
        ProcessRules processes,
        Set<ResourceLocation> notSame) {
    public static final ResolutionPolicy NONE = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of());

    public ResolutionPolicy {
        globalModPriority = List.copyOf(globalModPriority);
        materialModPriority = Map.copyOf(materialModPriority);
        formModPriority = Map.copyOf(formModPriority);
        explicitProviders = Map.copyOf(explicitProviders);
        excludedMaterials = Set.copyOf(excludedMaterials);
        excludedForms = Set.copyOf(excludedForms);
        conversionRecipeForms = Set.copyOf(conversionRecipeForms);
        notSame = Set.copyOf(notSame);
    }

    /** Without items marked "not the same" (MNX-050). */
    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders,
                            Set<MaterialId> excludedMaterials, Set<MaterialForm> excludedForms, Set<FormId> conversionRecipeForms,
                            AlmostUnified almostUnified, ProcessRules processes) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, excludedMaterials, excludedForms,
                conversionRecipeForms, almostUnified, processes, Set.of());
    }

    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders,
                            Set<MaterialId> excludedMaterials, Set<MaterialForm> excludedForms, Set<FormId> conversionRecipeForms,
                            AlmostUnified almostUnified) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, excludedMaterials, excludedForms,
                conversionRecipeForms, almostUnified, ProcessRules.NONE);
    }

    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders,
                            Set<MaterialId> excludedMaterials, Set<MaterialForm> excludedForms, Set<FormId> conversionRecipeForms) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, excludedMaterials, excludedForms,
                conversionRecipeForms, AlmostUnified.NONE);
    }

    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders,
                            Set<MaterialId> excludedMaterials, Set<MaterialForm> excludedForms) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, excludedMaterials, excludedForms, Set.of());
    }

    public ResolutionPolicy(List<String> globalModPriority, Map<MaterialId, List<String>> materialModPriority,
                            Map<MaterialForm, List<String>> formModPriority, Map<MaterialForm, ResourceLocation> explicitProviders) {
        this(globalModPriority, materialModPriority, formModPriority, explicitProviders, Set.of(), Set.of());
    }

    public boolean isExcluded(MaterialForm key) {
        return excludedMaterials.contains(key.material()) || excludedForms.contains(key);
    }

    /** This policy with pending process rule edits (MNX-036). */
    public ResolutionPolicy withProcesses(Map<FormId, ProcessRules.Rule> edits) {
        return new ResolutionPolicy(globalModPriority, materialModPriority, formModPriority, explicitProviders,
                excludedMaterials, excludedForms, conversionRecipeForms, almostUnified, processes.withForms(edits), notSame);
    }

    /** This policy plus extra explicit choices, used to preview pending GUI changes before they are written. */
    public ResolutionPolicy withExplicit(Map<MaterialForm, ResourceLocation> extra) {
        Map<MaterialForm, ResourceLocation> merged = new HashMap<>(explicitProviders);
        merged.putAll(extra);
        return new ResolutionPolicy(globalModPriority, materialModPriority, formModPriority, merged,
                excludedMaterials, excludedForms, conversionRecipeForms, almostUnified, processes, notSame);
    }
}
