package dev.drimoz.materialnexus.core.discovery;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
    private static final String ORES_IN_GROUND = "ores_in_ground/";
    private static final FormId ORE = new FormId("ore");
    private static final FormId BLOCK = new FormId("block");
    private static final FormId RAW_BLOCK = new FormId("raw_block");

    /** Convention tag folder to form. Folders absent here are ignored, not guessed. */
    private static final Map<String, FormId> FOLDERS = Map.ofEntries(
            Map.entry("ores", ORE),
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

    /** Host rock to ore form: stone and deepslate ores are variants, never duplicates of each other. */
    private static final Map<String, String> GROUNDS = Map.of(
            "stone", "ore", "deepslate", "deepslate_ore", "netherrack", "nether_ore", "end_stone", "end_ore");

    private TagDiscovery() { }

    public static DiscoveredMaterials discover(Map<ResourceLocation, ? extends Collection<ResourceLocation>> tagMembers) {
        Map<ResourceLocation, String> groundOf = groundsByItem(tagMembers);
        SortedMap<MaterialId, SortedMap<FormId, SortedMap<ResourceLocation, List<DiscoveryEvidence>>>> found = new TreeMap<>();

        tagMembers.forEach((tag, members) -> {
            if (!tag.getNamespace().equals(CONVENTION_NAMESPACE)) return;
            String[] parts = tag.getPath().split("/");
            if (parts.length != 2) return;
            FormId tagForm = FOLDERS.get(parts[0]);
            if (tagForm == null) return;

            String name = parts[1];
            // c:storage_blocks/raw_copper is the raw block of copper, not a material called raw_copper.
            if (tagForm.equals(BLOCK) && name.startsWith(RAW_PREFIX)) {
                tagForm = RAW_BLOCK;
                name = name.substring(RAW_PREFIX.length());
            }
            var material = MaterialId.read(name).result();
            if (material.isEmpty()) return;

            var byForm = found.computeIfAbsent(material.get(), m -> new TreeMap<>());
            for (ResourceLocation item : members) {
                FormId form = tagForm;
                String explanation = "#" + tag;
                if (tagForm.equals(ORE)) {
                    String ground = groundOf.get(item);
                    form = ground != null ? oreForm(ground) : oreFormByName(item);
                    explanation += ground != null ? " + #c:" + ORES_IN_GROUND + ground : " + name (" + form + ")";
                }
                byForm.computeIfAbsent(form, f -> new TreeMap<>())
                        .computeIfAbsent(item, i -> new ArrayList<>())
                        .add(new DiscoveryEvidence(Confidence.MATERIAL_TAG, explanation));
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

    private static Map<ResourceLocation, String> groundsByItem(Map<ResourceLocation, ? extends Collection<ResourceLocation>> tagMembers) {
        Map<ResourceLocation, String> ground = new HashMap<>();
        tagMembers.forEach((tag, members) -> {
            if (tag.getNamespace().equals(CONVENTION_NAMESPACE) && tag.getPath().startsWith(ORES_IN_GROUND)) {
                String name = tag.getPath().substring(ORES_IN_GROUND.length());
                for (ResourceLocation item : members) ground.merge(item, name, (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
        });
        return ground;
    }

    private static FormId oreForm(String ground) {
        String form = GROUNDS.getOrDefault(ground, ground + "_ore");
        return FormId.read(form).result().orElse(ORE);
    }

    /** Fallback when a mod does not tag the host rock: conservative name check, stone otherwise. */
    private static FormId oreFormByName(ResourceLocation item) {
        String path = item.getPath();
        if (path.contains("deepslate")) return oreForm("deepslate");
        if (path.contains("nether")) return oreForm("netherrack");
        if (path.startsWith("end_") || path.contains("end_stone")) return oreForm("end_stone");
        return ORE;
    }
}
