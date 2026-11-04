package dev.drimoz.materialnexus.diagnostics;

import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * MNX-090: the recipe types no recipe format covers, ranked by how many of their recipes name a material item, so the
 * mods worth a format are known instead of guessed. Pure: the caller supplies each recipe's JSON, so this runs only
 * for {@code /materials report}, never on load or per tick.
 */
public final class UnhandledTypes {
    /** {@code recipes}: recipes of this type naming an item of a material. {@code replaced}: those naming an item unification replaces. */
    public record Row(Identifier type, int recipes, int replaced) { }

    private UnhandledTypes() { }

    public static List<Row> rank(Map<Identifier, JsonElement> recipes, Predicate<Identifier> handled,
                                 Set<Identifier> materialItems, Set<Identifier> alternatives) {
        Map<Identifier, int[]> counts = new TreeMap<>();
        recipes.values().forEach(json -> {
            if (!json.isJsonObject() || !json.getAsJsonObject().has("type") || !json.getAsJsonObject().get("type").isJsonPrimitive()) return;
            Identifier type = Identifier.tryParse(json.getAsJsonObject().get("type").getAsString());
            if (type == null || handled.test(type)) return;
            Set<Identifier> ids = new HashSet<>();
            ids(json, ids);
            if (ids.stream().noneMatch(materialItems::contains)) return;
            int[] count = counts.computeIfAbsent(type, t -> new int[2]);
            count[0]++;
            if (ids.stream().anyMatch(alternatives::contains)) count[1]++;
        });
        return counts.entrySet().stream().map(e -> new Row(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(Row::replaced).reversed().thenComparing(Comparator.comparingInt(Row::recipes).reversed()).thenComparing(Row::type))
                .toList();
    }

    /** Every string of the recipe that reads as an id: where a type keeps its items is exactly what is not known. */
    private static void ids(JsonElement json, Set<Identifier> out) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(e -> ids(e.getValue(), out));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> ids(e, out));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            Identifier id = Identifier.tryParse(json.getAsString());
            if (id != null) out.add(id);
        }
    }
}
