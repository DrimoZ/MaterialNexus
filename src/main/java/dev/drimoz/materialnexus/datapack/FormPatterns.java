package dev.drimoz.materialnexus.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Forms as data (MNX-040/041), from {@code data/<namespace>/material_nexus/forms/*.json}:
 * {@code {"folders": {"wires": "wire"}, "patterns": {"double_ingot": ["modern_industrialization:{material}_double_ingot"]},
 * "relations": [{"from": "tiny_dust", "to": "dust"}], "remove_relations": [...], "remove_folders": ["sheetmetals"]}}.
 * Folders add convention tag folders to the built-in ones (remove_folders drops any, built-in included); patterns find forms mods leave untagged; relations add or
 * remove the conversions checked for missing recipes. Declared data,
 * not a guess: a pattern item only counts when {@code {material}} names a material the tags already know.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class FormPatterns extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "material_nexus/forms";
    private static final String MATERIAL = "{material}";
    private static final Logger LOGGER = LogUtils.getLogger();
    private record FormsFile(Map<String, FormId> folders, Map<FormId, List<String>> patterns,
                             List<dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation> relations,
                             List<dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation> removeRelations,
                             List<String> removeFolders) { }

    /** {@code {"from": "nugget", "to": "ingot"}}: a recipe should turn one into the other (missing-recipe proposals). */
    private static final Codec<dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation> RELATION =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                    FormId.CODEC.fieldOf("from").forGetter(dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation::from),
                    FormId.CODEC.fieldOf("to").forGetter(dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation::to)
            ).apply(i, dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation::new));

    private static final Codec<FormsFile> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, FormId.CODEC).optionalFieldOf("folders", Map.of()).forGetter(FormsFile::folders),
            Codec.unboundedMap(FormId.CODEC, Codec.STRING.listOf()).optionalFieldOf("patterns", Map.of()).forGetter(FormsFile::patterns),
            RELATION.listOf().optionalFieldOf("relations", List.of()).forGetter(FormsFile::relations),
            RELATION.listOf().optionalFieldOf("remove_relations", List.of()).forGetter(FormsFile::removeRelations),
            Codec.STRING.listOf().optionalFieldOf("remove_folders", List.of()).forGetter(FormsFile::removeFolders)
    ).apply(i, FormsFile::new));
    private static volatile Map<FormId, List<String>> patterns = Map.of();

    private FormPatterns() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new FormPatterns());
    }

    /** Why this forms file cannot be read, or empty (MNX-046 in-game edits). */
    public static java.util.Optional<String> validate(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).error().map(e -> e.message());
    }

    public static Map<FormId, List<String>> patterns() {
        return patterns;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, FormsFile> parsed = new TreeMap<>();
        new TreeMap<>(files).forEach((file, json) -> CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.warn("Ignoring invalid forms file {}: {}", file, error))
                .ifPresent(f -> parsed.put(file, f)));
        Map<String, FormId> folders = new TreeMap<>();
        parsed.values().forEach(f -> folders.putAll(f.folders()));
        TagDiscovery.setDataFolders(folders, parsed.values().stream().flatMap(f -> f.removeFolders().stream()).toList());
        dev.drimoz.materialnexus.core.recipe.FamilyRelations.setData(
                parsed.values().stream().flatMap(f -> f.relations().stream()).toList(),
                parsed.values().stream().flatMap(f -> f.removeRelations().stream()).toList());
        Map<FormId, List<String>> merged = new TreeMap<>();
        parsed.forEach((file, f) -> f.patterns().forEach((form, list) -> {
            if (TagDiscovery.folder(form).isEmpty()) LOGGER.warn("{}: form '{}' has no tag folder (add one under \"folders\"), ignored", file, form.name());
            else merged.computeIfAbsent(form, x -> new ArrayList<>()).addAll(list);
        }));
        patterns = Map.copyOf(merged);
    }

    /**
     * Adds every item matching a pattern for a known material under {@code materialnexus:pattern/<folder>/<material>},
     * which discovery reads like a convention tag. Pure. {@code knownNames}: material names (and aliases) from the tags.
     */
    public static void inject(Map<ResourceLocation, List<ResourceLocation>> tagMembers, Collection<ResourceLocation> items,
                              Map<FormId, List<String>> patterns, Set<String> knownNames) {
        patterns.forEach((form, list) -> TagDiscovery.folder(form).ifPresent(folder -> {
            for (String pattern : list) {
                int colon = pattern.indexOf(':');
                int at = pattern.indexOf(MATERIAL);
                if (colon < 0 || at < colon) continue;
                String namespace = pattern.substring(0, colon);
                String prefix = pattern.substring(colon + 1, at);
                String suffix = pattern.substring(at + MATERIAL.length());
                for (ResourceLocation item : items) {
                    String path = item.getPath();
                    if (!item.getNamespace().equals(namespace) || !path.startsWith(prefix) || !path.endsWith(suffix)
                            || path.length() <= prefix.length() + suffix.length()) continue;
                    String name = path.substring(prefix.length(), path.length() - suffix.length());
                    if (!knownNames.contains(name)) continue;
                    ResourceLocation key = ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, TagDiscovery.PATTERN_PREFIX + folder + "/" + name);
                    List<ResourceLocation> members = tagMembers.computeIfAbsent(key, k -> new ArrayList<>());
                    if (!members.contains(item)) members.add(item);
                }
            }
        }));
    }

    /** An item name shape seen for several materials without being discovered: a pattern to review and maybe declare. */
    public record Candidate(String pattern, List<String> materials) { }

    /**
     * MNX-041 audit: undiscovered items whose name contains a known material (longest match), grouped by the shape of
     * the rest of the name; shapes seen for at least {@code minMaterials} materials, most common first.
     */
    public static List<Candidate> candidates(Collection<ResourceLocation> items, Set<String> knownNames, Set<ResourceLocation> discovered,
                                             int minMaterials) {
        Map<String, Set<String>> byPattern = new TreeMap<>();
        for (ResourceLocation item : items) {
            if (discovered.contains(item)) continue;
            String[] tokens = item.getPath().split("_");
            int bestFrom = -1, bestTo = -1;
            for (int from = 0; from < tokens.length; from++) {
                for (int to = tokens.length; to > from; to--) {
                    if (to - from > bestTo - bestFrom && knownNames.contains(String.join("_", java.util.Arrays.copyOfRange(tokens, from, to)))) {
                        bestFrom = from;
                        bestTo = to;
                    }
                }
            }
            if (bestFrom < 0 || (bestFrom == 0 && bestTo == tokens.length)) continue;
            String prefix = bestFrom == 0 ? "" : String.join("_", java.util.Arrays.copyOfRange(tokens, 0, bestFrom)) + "_";
            String suffix = bestTo == tokens.length ? "" : "_" + String.join("_", java.util.Arrays.copyOfRange(tokens, bestTo, tokens.length));
            byPattern.computeIfAbsent(item.getNamespace() + ":" + prefix + MATERIAL + suffix, k -> new java.util.TreeSet<>())
                    .add(String.join("_", java.util.Arrays.copyOfRange(tokens, bestFrom, bestTo)));
        }
        return byPattern.entrySet().stream()
                .filter(e -> e.getValue().size() >= minMaterials)
                .sorted(java.util.Comparator.comparing((Map.Entry<String, Set<String>> e) -> -e.getValue().size()).thenComparing(Map.Entry::getKey))
                .map(e -> new Candidate(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    /** Material names the convention tags already name: {@code c:<known folder>/<name>}. */
    public static Set<String> knownNames(Collection<ResourceLocation> tags, Collection<String> aliases) {
        Set<String> names = new HashSet<>(aliases);
        for (ResourceLocation tag : tags) {
            String[] parts = tag.getPath().split("/");
            if (tag.getNamespace().equals("c") && parts.length == 2 && TagDiscovery.isFolder(parts[0])) names.add(parts[1]);
        }
        return names;
    }
}
