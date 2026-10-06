package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Picks one canonical provider per material/form. Pure and deterministic: providers are ranked
 * by policy level, then confidence, then resource id, so input order never matters.
 */
public final class CanonicalResolver {
    private static final String VANILLA = "minecraft";
    private static final Comparator<Provider> TIE_BREAK = Comparator
            .comparing(Provider::confidence)
            .thenComparing(Provider::resource);

    private CanonicalResolver() { }

    public static SortedMap<MaterialId, ResolvedMaterial> resolve(DiscoveredMaterials discovered, ResolutionPolicy policy) {
        SortedMap<MaterialId, ResolvedMaterial> result = new TreeMap<>();
        discovered.materials().forEach((material, forms) -> {
            SortedMap<FormId, ResolvedForm> resolved = new TreeMap<>();
            forms.forEach((form, providers) -> {
                if (!providers.isEmpty()) resolved.put(form, resolveForm(new MaterialForm(material, form), providers, policy));
            });
            result.put(material, new ResolvedMaterial(material, Collections.unmodifiableSortedMap(resolved)));
        });
        return Collections.unmodifiableSortedMap(result);
    }

    static ResolvedForm resolveForm(MaterialForm key, List<Provider> providers, ResolutionPolicy policy) {
        ResourceLocation explicit = policy.explicitProviders().get(key);
        if (explicit != null) {
            for (Provider p : providers) {
                if (p.resource().equals(explicit)) {
                    return result(p, providers, PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE, "explicit override for " + key);
                }
            }
        }
        String missing = explicit == null ? "" : " (explicit override " + explicit + " is not a discovered provider, ignored)";

        ResolvedForm byPriority = firstByModPriority(key, providers, policy.formModPriority().get(key), PolicyPrecedence.FORM, "form priority for " + key, missing);
        if (byPriority == null) byPriority = firstByModPriority(key, providers, policy.materialModPriority().get(key.material()), PolicyPrecedence.MATERIAL, "material priority for " + key.material(), missing);
        if (byPriority == null) byPriority = firstByModPriority(key, providers, policy.globalModPriority(), PolicyPrecedence.GLOBAL, "global mod priority", missing);
        if (byPriority != null) return byPriority;

        if (providers.size() == 1) return result(providers.getFirst(), providers, PolicyPrecedence.DEFAULT, "only provider" + missing);
        List<Provider> vanilla = providers.stream().filter(p -> p.sourceMod().equals(VANILLA)).toList();
        if (!vanilla.isEmpty()) {
            return result(vanilla.stream().min(TIE_BREAK).orElseThrow(), providers, PolicyPrecedence.DEFAULT, "vanilla item preferred by default" + missing);
        }
        return result(providers.stream().min(TIE_BREAK).orElseThrow(), providers, PolicyPrecedence.DEFAULT,
                "no policy: strongest evidence, then alphabetical id" + missing);
    }

    private static ResolvedForm firstByModPriority(MaterialForm key, List<Provider> providers, List<String> priority,
                                                   PolicyPrecedence source, String label, String missing) {
        if (priority == null) return null;
        for (String mod : priority) {
            var match = providers.stream().filter(p -> p.sourceMod().equals(mod)).min(TIE_BREAK);
            if (match.isPresent()) return result(match.get(), providers, source, mod + " first in " + label + missing);
        }
        return null;
    }

    private static ResolvedForm result(Provider canonical, List<Provider> providers, PolicyPrecedence source, String reason) {
        List<ResourceLocation> alternatives = providers.stream()
                .map(Provider::resource)
                .filter(r -> !r.equals(canonical.resource()))
                .sorted()
                .toList();
        return new ResolvedForm(canonical.resource(), alternatives, source, canonical.confidence(), reason);
    }
}
