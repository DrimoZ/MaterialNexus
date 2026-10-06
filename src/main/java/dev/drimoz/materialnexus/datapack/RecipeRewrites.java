package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Recipe side of an applied unification (ADR-011): recipes producing an alternative get the canonical
 * output. Only vanilla recipe types are rewritten; their format is known. Other types are listed as
 * unsupported and left untouched until their adapter exists. A rewrite that becomes an exact
 * duplicate of another recipe is disabled instead. Pure: the server adapter supplies the sources.
 */
public final class RecipeRewrites {
    public static final String REWRITE = "recipe_rewrite";
    public static final String DISABLE = "recipe_disable";
    public static final String UNSUPPORTED = "recipe_unsupported";

    /**
     * One recipe as it was before Material Nexus. {@code json} is present for vanilla types only;
     * {@code result} is the item it produces.
     */
    public record Source(ResourceLocation id, Optional<JsonObject> json, ResourceLocation result) { }

    private RecipeRewrites() { }

    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions) {
        Map<String, JsonElement> files = new TreeMap<>();
        List<PackContent.Effect> effects = new ArrayList<>();
        Map<ResourceLocation, JsonObject> rewritten = new TreeMap<>();

        for (Source s : sources) {
            ResourceLocation canonical = conversions.get(s.result());
            if (canonical == null) continue;
            if (s.json().isEmpty()) {
                effects.add(new PackContent.Effect(UNSUPPORTED, s.id(), s.result()));
                continue;
            }
            rewritten.put(s.id(), withResult(s.json().get(), canonical));
        }

        // Exact duplicates after rewrite: an untouched recipe, or a rewritten one with a smaller id, wins.
        Map<JsonElement, ResourceLocation> keeper = new HashMap<>();
        sources.stream()
                .filter(s -> s.json().isPresent() && !rewritten.containsKey(s.id()) && conversions.containsValue(s.result()))
                .sorted(Comparator.comparing(Source::id))
                .forEach(s -> keeper.putIfAbsent(normalized(s.json().get()), s.id()));

        rewritten.forEach((id, json) -> {
            ResourceLocation result = resultId(json).orElseThrow();
            if (keeper.putIfAbsent(normalized(json), id) != null) {
                files.put(path(id), disabled());
                effects.add(new PackContent.Effect(DISABLE, id, result));
            } else {
                files.put(path(id), json);
                effects.add(new PackContent.Effect(REWRITE, id, result));
            }
        });
        return new PackContent.Content(files, effects);
    }

    /** Recipe ids this pack overrides; their pre-MNX JSON must be read beneath it (ADR-010). */
    public static List<ResourceLocation> overridden(List<PackContent.Effect> manifest) {
        return manifest.stream().filter(e -> e.kind().equals(REWRITE) || e.kind().equals(DISABLE)).map(PackContent.Effect::target).toList();
    }

    public static Optional<ResourceLocation> resultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null) return Optional.empty();
        if (result.isJsonPrimitive()) return Optional.ofNullable(ResourceLocation.tryParse(result.getAsString()));
        if (result.isJsonObject()) {
            JsonObject o = result.getAsJsonObject();
            JsonElement id = o.has("id") ? o.get("id") : o.get("item");
            if (id != null && id.isJsonPrimitive()) return Optional.ofNullable(ResourceLocation.tryParse(id.getAsString()));
        }
        return Optional.empty();
    }

    private static JsonObject withResult(JsonObject recipe, ResourceLocation canonical) {
        JsonObject copy = recipe.deepCopy();
        JsonElement result = copy.get("result");
        if (result.isJsonPrimitive()) {
            copy.addProperty("result", canonical.toString());
        } else {
            JsonObject r = result.getAsJsonObject();
            r.remove("item");
            r.addProperty("id", canonical.toString());
        }
        return copy;
    }

    /** What makes two recipes the same for a player: everything but grouping and book category. */
    private static JsonElement normalized(JsonObject recipe) {
        JsonObject copy = recipe.deepCopy();
        copy.remove("group");
        copy.remove("category");
        copy.remove("show_notification");
        return copy;
    }

    private static JsonObject disabled() {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "neoforge:false");
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        JsonObject recipe = new JsonObject();
        recipe.add("neoforge:conditions", conditions);
        return recipe;
    }

    private static String path(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json";
    }
}
