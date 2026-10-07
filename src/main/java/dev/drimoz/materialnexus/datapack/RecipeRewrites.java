package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Recipe side of an applied unification (ADR-011): recipes whose type has a known format
 * ({@link RecipeFormats}, data-driven) get canonical outputs and inputs. Other types producing an
 * alternative are listed as unsupported and left untouched until a format describes them. A rewrite
 * that becomes an exact duplicate of another recipe is disabled instead. Pure: the server adapter
 * supplies the sources and the loaded formats.
 */
public final class RecipeRewrites {
    public static final String REWRITE = "recipe_rewrite";
    public static final String DISABLE = "recipe_disable";
    public static final String UNSUPPORTED = "recipe_unsupported";

    /**
     * One recipe as it was before Material Nexus. {@code json} is present when its type has a known
     * format; {@code result} is the (first) item it produces.
     */
    public record Source(ResourceLocation id, Optional<JsonObject> json, ResourceLocation result) { }

    private RecipeRewrites() { }

    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats) {
        return plan(sources, conversions, formats, AlmostUnified.Ownership.ALL);
    }

    /** With Almost Unified owning recipe rewriting, nothing is planned; owning only disabling, duplicates are rewritten, not disabled. */
    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                           AlmostUnified.Ownership ownership) {
        if (!ownership.outputRewrite()) return new PackContent.Content(Map.of(), List.of());
        Map<String, JsonElement> files = new TreeMap<>();
        List<PackContent.Effect> effects = new ArrayList<>();
        Map<ResourceLocation, JsonObject> rewritten = new TreeMap<>();

        for (Source s : sources) {
            Optional<RecipeFormats.Format> format = s.json().flatMap(json -> format(json, formats));
            if (format.isEmpty()) {
                if (conversions.containsKey(s.result())) effects.add(new PackContent.Effect(UNSUPPORTED, s.id(), s.result()));
                continue;
            }
            JsonObject original = s.json().get();
            // Inputs too: an alternative named as a literal item would otherwise be asked for after it was converted away.
            JsonObject json = withOutputs(withInputs(original, conversions, format.get().outputKeys()), format.get().outputKeys(), conversions);
            if (!json.equals(original)) rewritten.put(s.id(), json);
        }

        // Exact duplicates after rewrite: an untouched recipe, or a rewritten one with a smaller id, wins.
        Map<JsonElement, ResourceLocation> keeper = new HashMap<>();
        sources.stream()
                .filter(s -> s.json().isPresent() && !rewritten.containsKey(s.id()))
                .sorted(Comparator.comparing(Source::id))
                .forEach(s -> keeper.putIfAbsent(normalized(s.json().get()), s.id()));

        Map<ResourceLocation, ResourceLocation> resultOf = new HashMap<>();
        sources.forEach(s -> resultOf.put(s.id(), conversions.getOrDefault(s.result(), s.result())));
        rewritten.forEach((id, json) -> {
            ResourceLocation result = resultOf.get(id);
            if (keeper.putIfAbsent(normalized(json), id) != null && ownership.recipeDisable()) {
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
        return manifest.stream().filter(e -> e.kind().equals(REWRITE) || e.kind().equals(DISABLE) || e.kind().equals(ProcessPlanner.DISABLE))
                .map(PackContent.Effect::target).toList();
    }

    public static Optional<RecipeFormats.Format> format(JsonObject recipe, RecipeFormats formats) {
        JsonElement type = recipe.get("type");
        if (type == null || !type.isJsonPrimitive()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(type.getAsString());
        return id == null ? Optional.empty() : formats.forType(id);
    }

    /** Every item a recipe produces, in output-key order. Empty when its format is unknown. */
    public static List<ResourceLocation> outputIds(JsonObject recipe, RecipeFormats formats) {
        List<ResourceLocation> ids = new ArrayList<>();
        format(recipe, formats).ifPresent(f -> f.outputKeys().forEach(key -> collectIds(recipe.get(key), ids)));
        return ids;
    }

    /** Item ids under an output key: a stack ({@code "id"} or {@code "item"}), a bare id, or nested in lists and objects. */
    private static void collectIds(JsonElement output, List<ResourceLocation> ids) {
        if (output == null) return;
        if (output.isJsonArray()) {
            output.getAsJsonArray().forEach(e -> collectIds(e, ids));
        } else if (output.isJsonPrimitive()) {
            Optional.ofNullable(ResourceLocation.tryParse(output.getAsString())).ifPresent(ids::add);
        } else if (output.isJsonObject()) {
            JsonObject o = output.getAsJsonObject();
            JsonElement id = o.has("id") ? o.get("id") : o.get("item");
            if (id != null && id.isJsonPrimitive()) Optional.ofNullable(ResourceLocation.tryParse(id.getAsString())).ifPresent(ids::add);
            else o.entrySet().forEach(e -> { if (!e.getValue().isJsonPrimitive()) collectIds(e.getValue(), ids); });
        }
    }

    /** Replaces alternatives in the outputs (a stack, a bare id, or nested in lists and objects such as IE secondaries). */
    static JsonObject withOutputs(JsonObject recipe, List<String> outputKeys, Map<ResourceLocation, ResourceLocation> conversions) {
        JsonObject copy = recipe.deepCopy();
        for (String key : outputKeys) {
            JsonElement output = copy.get(key);
            if (output == null) continue;
            if (output.isJsonPrimitive()) {
                ResourceLocation id = ResourceLocation.tryParse(output.getAsString());
                if (id != null && conversions.containsKey(id)) copy.addProperty(key, conversions.get(id).toString());
            } else {
                replaceStacks(output, conversions);
            }
        }
        return copy;
    }

    private static void replaceStacks(JsonElement output, Map<ResourceLocation, ResourceLocation> conversions) {
        if (output.isJsonArray()) {
            output.getAsJsonArray().forEach(e -> replaceStacks(e, conversions));
        } else if (output.isJsonObject()) {
            JsonObject o = output.getAsJsonObject();
            String field = o.has("id") ? "id" : o.has("item") ? "item" : null;
            JsonElement id = field == null ? null : o.get(field);
            if (id != null && id.isJsonPrimitive()) {
                ResourceLocation item = ResourceLocation.tryParse(id.getAsString());
                if (item != null && conversions.containsKey(item)) o.addProperty(field, conversions.get(item).toString());
            } else {
                o.entrySet().forEach(e -> replaceStacks(e.getValue(), conversions));
            }
        }
    }

    /** Replaces every literal {@code "item": <alternative>} outside the outputs; tag ingredients are left as they are. */
    static JsonObject withInputs(JsonObject recipe, Map<ResourceLocation, ResourceLocation> conversions, List<String> outputKeys) {
        JsonObject copy = recipe.deepCopy();
        for (String key : copy.keySet()) {
            if (!outputKeys.contains(key)) replaceItems(copy.get(key), conversions);
        }
        return copy;
    }

    private static void replaceItems(JsonElement element, Map<ResourceLocation, ResourceLocation> conversions) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> replaceItems(e, conversions));
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            JsonElement item = o.get("item");
            if (item != null && item.isJsonPrimitive()) {
                ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
                ResourceLocation canonical = id == null ? null : conversions.get(id);
                if (canonical != null) o.addProperty("item", canonical.toString());
            }
            o.entrySet().forEach(e -> replaceItems(e.getValue(), conversions));
        }
    }

    /** What makes two recipes the same for a player: everything but grouping and book category. */
    private static JsonElement normalized(JsonObject recipe) {
        JsonObject copy = recipe.deepCopy();
        copy.remove("group");
        copy.remove("category");
        copy.remove("show_notification");
        return copy;
    }

    static JsonObject disabled() {
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
