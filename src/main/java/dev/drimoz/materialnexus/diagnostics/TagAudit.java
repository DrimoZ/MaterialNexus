package dev.drimoz.materialnexus.diagnostics;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * MNX-080: the tags recipes ask for that cannot match anything. Pure: the caller supplies each recipe's JSON and which
 * tags have items, so this runs only for {@code /materials report}, never on load or per tick.
 */
public final class TagAudit {
    /**
     * {@code forge}: recipes asking for a {@code forge:} tag (1.21 uses {@code c:}), with whether the tag has items.
     * {@code emptyMaterial}: recipes asking for a {@code c:<folder>/<material>} tag with no item: they cannot be made.
     */
    public record Result(SortedMap<ResourceLocation, SortedSet<ResourceLocation>> forge, Set<ResourceLocation> forgeFilled,
                         SortedMap<ResourceLocation, SortedSet<ResourceLocation>> emptyMaterial) {
        public static final Result NONE = new Result(new TreeMap<>(), Set.of(), new TreeMap<>());
    }

    private TagAudit() { }

    public static Result of(Map<ResourceLocation, JsonElement> recipes, Predicate<ResourceLocation> hasItems, Predicate<String> isFolder) {
        SortedMap<ResourceLocation, SortedSet<ResourceLocation>> forge = new TreeMap<>();
        SortedMap<ResourceLocation, SortedSet<ResourceLocation>> empty = new TreeMap<>();
        Set<ResourceLocation> filled = new TreeSet<>();
        recipes.forEach((id, json) -> {
            Set<ResourceLocation> tags = new TreeSet<>();
            tagRefs(json, tags);
            for (ResourceLocation tag : tags) {
                if (tag.getNamespace().equals("forge")) {
                    forge.computeIfAbsent(tag, t -> new TreeSet<>()).add(id);
                    if (hasItems.test(tag)) filled.add(tag);
                } else if (tag.getNamespace().equals("c") && materialTag(tag, isFolder) && !hasItems.test(tag)) {
                    empty.computeIfAbsent(tag, t -> new TreeSet<>()).add(id);
                }
            }
        });
        return new Result(forge, Set.copyOf(filled), empty);
    }

    /** c:ingots/tin, not c:ingots nor c:tools/... */
    private static boolean materialTag(ResourceLocation tag, Predicate<String> isFolder) {
        String[] parts = tag.getPath().split("/");
        return parts.length == 2 && isFolder.test(parts[0]);
    }

    /** {@code "tag": "c:ingots/tin"} anywhere (vanilla, Create, Mekanism...) and {@code "#c:ingots/tin"} strings. */
    static void tagRefs(JsonElement json, Set<ResourceLocation> out) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(e -> {
                if (e.getKey().equals("tag") && e.getValue().isJsonPrimitive()) add(e.getValue().getAsString(), out);
                else tagRefs(e.getValue(), out);
            });
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> tagRefs(e, out));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && json.getAsString().startsWith("#")) {
            add(json.getAsString().substring(1), out);
        }
    }

    private static void add(String id, Set<ResourceLocation> out) {
        ResourceLocation tag = ResourceLocation.tryParse(id);
        if (tag != null) out.add(tag);
    }
}
