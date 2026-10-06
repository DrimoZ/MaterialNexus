package dev.drimoz.materialnexus.core.discovery;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Turns {@code c:<folder>/<material>} item tags into material/form providers. Pure: the caller
 * supplies tag memberships, so this never touches registries and never runs on a tick.
 */
public final class TagDiscovery {
    private static final String CONVENTION_NAMESPACE = "c";
    private static final String RAW_PREFIX = "raw_";
    private static final FormId BLOCK = new FormId("block");
    private static final FormId RAW_BLOCK = new FormId("raw_block");

    /** Convention tag folder → form. Folders absent here are ignored, not guessed. */
    private static final Map<String, FormId> FOLDERS = Map.ofEntries(
            Map.entry("ores", new FormId("ore")),
            Map.entry("raw_materials", new FormId("raw")),
            Map.entry("storage_blocks", BLOCK),
            Map.entry("ingots", new FormId("ingot")),
            Map.entry("nuggets", new FormId("nugget")),
            Map.entry("gems", new FormId("gem")),
            Map.entry("dusts", new FormId("dust")),
            Map.entry("plates", new FormId("plate")),
            Map.entry("rods", new FormId("rod")),
            Map.entry("gears", new FormId("gear")),
            Map.entry("wires", new FormId("wire")));

    private TagDiscovery() { }

    public static DiscoveredMaterials discover(Map<ResourceLocation, ? extends Collection<ResourceLocation>> tagMembers) {
        SortedMap<MaterialId, SortedMap<FormId, SortedMap<ResourceLocation, List<DiscoveryEvidence>>>> found = new TreeMap<>();

        tagMembers.forEach((tag, members) -> {
            if (!tag.getNamespace().equals(CONVENTION_NAMESPACE)) return;
            String[] parts = tag.getPath().split("/");
            if (parts.length != 2) return;
            FormId form = FOLDERS.get(parts[0]);
            if (form == null) return;

            String name = parts[1];
            // c:storage_blocks/raw_copper is the raw block of copper, not a material called raw_copper.
            if (form.equals(BLOCK) && name.startsWith(RAW_PREFIX)) {
                form = RAW_BLOCK;
                name = name.substring(RAW_PREFIX.length());
            }
            var material = MaterialId.read(name).result();
            if (material.isEmpty()) return;

            var evidence = new DiscoveryEvidence(Confidence.MATERIAL_TAG, "#" + tag);
            var byResource = found.computeIfAbsent(material.get(), m -> new TreeMap<>()).computeIfAbsent(form, f -> new TreeMap<>());
            for (ResourceLocation item : members) {
                byResource.computeIfAbsent(item, i -> new ArrayList<>()).add(evidence);
            }
        });

        SortedMap<MaterialId, SortedMap<FormId, List<Provider>>> materials = new TreeMap<>();
        found.forEach((material, forms) -> {
            SortedMap<FormId, List<Provider>> byForm = new TreeMap<>();
            forms.forEach((form, byResource) -> byForm.put(form, byResource.entrySet().stream()
                    .map(e -> new Provider(e.getKey(), material, form, e.getValue()))
                    .toList()));
            materials.put(material, Collections.unmodifiableSortedMap(byForm));
        });
        return new DiscoveredMaterials(Collections.unmodifiableSortedMap(materials));
    }
}
