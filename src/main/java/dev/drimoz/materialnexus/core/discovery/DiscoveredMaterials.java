package dev.drimoz.materialnexus.core.discovery;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

/** Discovery result: material → form → providers, all sorted so the same pack always yields the same result. */
public record DiscoveredMaterials(SortedMap<MaterialId, SortedMap<FormId, List<Provider>>> materials) {
    public static final DiscoveredMaterials EMPTY = new DiscoveredMaterials(Collections.emptySortedMap());

    public List<Provider> providers(MaterialId material, FormId form) {
        var forms = materials.get(material);
        return forms == null ? List.of() : forms.getOrDefault(form, List.of());
    }

    public int providerCount() {
        return materials.values().stream().flatMap(f -> f.values().stream()).mapToInt(List::size).sum();
    }
}
