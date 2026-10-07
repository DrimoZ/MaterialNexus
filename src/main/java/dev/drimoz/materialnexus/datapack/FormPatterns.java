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
 * Item name patterns for forms mods leave untagged (MNX-040), from
 * {@code data/<namespace>/material_nexus/form_patterns/*.json}:
 * {@code {"patterns": {"double_ingot": ["modern_industrialization:{material}_double_ingot"]}}}.
 * Declared data, not a guess: an item only counts when {@code {material}} names a material the tags already know.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class FormPatterns extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "material_nexus/form_patterns";
    private static final String MATERIAL = "{material}";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<FormId, List<String>>> CODEC =
            Codec.unboundedMap(FormId.CODEC, Codec.STRING.listOf()).fieldOf("patterns").codec();
    private static volatile Map<FormId, List<String>> patterns = Map.of();

    private FormPatterns() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new FormPatterns());
    }

    public static Map<FormId, List<String>> patterns() {
        return patterns;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<FormId, List<String>> merged = new TreeMap<>();
        new TreeMap<>(files).forEach((file, json) -> CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.warn("Ignoring invalid form pattern file {}: {}", file, error))
                .ifPresent(p -> p.forEach((form, list) -> {
                    if (TagDiscovery.folder(form).isEmpty()) LOGGER.warn("{}: form '{}' is not a known form, ignored", file, form.name());
                    else merged.computeIfAbsent(form, f -> new ArrayList<>()).addAll(list);
                })));
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
