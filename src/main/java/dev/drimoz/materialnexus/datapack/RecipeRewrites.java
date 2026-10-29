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
 * Recipe side of an applied unification (ADR-011, ADR-025): a recipe naming an alternative gets the kept item in its
 * outputs and, in its inputs, the form's tag when that tag holds only the material, else the kept item. Where each
 * item sits comes from the recipe format when one covers the type ({@link RecipeFormats}, data-driven), otherwise from
 * the top-level key holding it, confirmed by decoding when the game exposes the recipe. Alternatives under any other
 * key are left alone and listed as unsupported. A rewrite that becomes an exact duplicate of another recipe is
 * disabled instead. Pure: the server adapter supplies the sources, the formats, the tags and the decoder.
 */
public final class RecipeRewrites {
    public static final String REWRITE = "recipe_rewrite";
    public static final String DISABLE = "recipe_disable";
    public static final String UNSUPPORTED = "recipe_unsupported";
    /** Evidence for a rewrite made without a format: {@code target} the recipe, {@code item} its type. */
    public static final String BY_KEYS = "recipe_by_keys";

    /** Top-level keys whose role is known without a format (the list Almost Unified uses). */
    // MNX-125, read off the 300 most downloaded Forge 1.20.1 mods of the technology, magic and equipment categories: a key
    // is added when several mods use it for one role (key: shaped grids; template, base, addition: smithing; itemInput:
    // Mekanism and Immersive Geology).
    static final List<String> INPUT_KEYS = List.of("input", "inputs", "ingredient", "ingredients", "inputItems", "itemInput",
            "key", "template", "base", "addition");
    static final List<String> OUTPUT_KEYS = List.of("output", "outputs", "result", "results", "outputItems");

    /**
     * One recipe as it was before Material Nexus. {@code json} is absent for a recipe with no file (made in memory);
     * {@code result} is the (first) item it produces.
     */
    public record Source(ResourceLocation id, Optional<JsonObject> json, ResourceLocation result) { }

    /** A recipe as the game reads it. {@code exposed}: the recipe tells its ingredients or result (GregTech, Thermal do not). */
    public interface Decoded {
        boolean exposed();
        boolean produces(ResourceLocation item);
        boolean consumes(ResourceLocation item);
    }

    /** Reads a recipe the way the game would; empty when the game refuses it. */
    public interface Decoder {
        Decoder NONE = json -> Optional.empty();
        Optional<Decoded> decode(JsonObject recipe);
    }

    private RecipeRewrites() { }

    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats) {
        return plan(sources, conversions, formats, AlmostUnified.Ownership.ALL);
    }

    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                           AlmostUnified.Ownership ownership) {
        return plan(sources, conversions, formats, ownership, Map.of(), Decoder.NONE);
    }

    /**
     * With Almost Unified owning recipe rewriting, nothing is planned; owning only disabling, duplicates are rewritten, not disabled.
     * {@code inputTags}: alternative to the tag an input naming it may become ({@link #inputTags}); {@code decoder} checks that tag.
     */
    public static PackContent.Content plan(List<Source> sources, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                           AlmostUnified.Ownership ownership, Map<ResourceLocation, ResourceLocation> inputTags, Decoder decoder) {
        if (!ownership.outputRewrite()) return new PackContent.Content(Map.of(), List.of());
        Map<String, JsonElement> files = new TreeMap<>();
        List<PackContent.Effect> effects = new ArrayList<>();
        Map<ResourceLocation, JsonObject> rewritten = new TreeMap<>();

        for (Source s : sources) {
            if (s.json().isEmpty()) {
                if (conversions.containsKey(s.result())) effects.add(new PackContent.Effect(UNSUPPORTED, s.id(), s.result()));
                continue;
            }
            JsonObject original = s.json().get();
            JsonObject json = original.deepCopy();
            // A wrapper (forge:conditional, meadow:conditional) holds the real recipes: each is read on its own.
            for (JsonObject recipe : recipes(json)) {
                rewrite(recipe, conversions, formats, inputTags, decoder)
                        .ifPresent(type -> effects.add(new PackContent.Effect(BY_KEYS, s.id(), type)));
            }
            // What is left names an alternative where no role is known (a structure block, a group): listed, untouched.
            Optional<ResourceLocation> left = firstMention(json, conversions.keySet());
            if (left.isPresent()) effects.add(new PackContent.Effect(UNSUPPORTED, s.id(), left.get()));
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

    /** A format file said never to touch this type (MNX-125). */
    private static boolean skipped(JsonObject recipe, RecipeFormats formats) {
        JsonElement type = recipe.get("type");
        ResourceLocation id = type != null && type.isJsonPrimitive() ? ResourceLocation.tryParse(type.getAsString()) : null;
        return id != null && formats.skips(id);
    }

    /** The recipe itself, then those a wrapper holds under {@code recipe} or {@code recipes[].recipe} (Forge conditional recipes), in place. */
    private static List<JsonObject> recipes(JsonObject json) {
        List<JsonObject> out = new ArrayList<>(List.of(json));
        JsonElement one = json.get("recipe");
        if (one != null && one.isJsonObject()) out.addAll(recipes(one.getAsJsonObject()));
        JsonElement many = json.get("recipes");
        if (many != null && many.isJsonArray()) {
            for (JsonElement e : many.getAsJsonArray()) {
                JsonElement inner = e.isJsonObject() ? e.getAsJsonObject().get("recipe") : null;
                if (inner != null && inner.isJsonObject()) out.addAll(recipes(inner.getAsJsonObject()));
            }
        }
        return out;
    }

    /**
     * One recipe, in place: with a format, every key outside its outputs is an input; without, the role comes from the key and
     * the game, when it shows the recipe, must not say otherwise (ADR-025). The type when keys alone made the change.
     */
    private static Optional<ResourceLocation> rewrite(JsonObject json, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                                      Map<ResourceLocation, ResourceLocation> inputTags, Decoder decoder) {
        JsonObject before = json.deepCopy();
        if (skipped(json, formats)) return Optional.empty();
        Optional<RecipeFormats.Format> format = format(json, formats);
        List<JsonObject> inputs = new ArrayList<>();
        if (format.isPresent()) {
            List<String> outputKeys = format.get().outputKeys();
            // Inputs too: an alternative named as a literal item would otherwise be asked for after it was converted away.
            // Load conditions name items too: kept item there, never a tag. A wrapped recipe is rewritten on its own.
            json.keySet().stream().filter(k -> !outputKeys.contains(k) && !k.equals("recipe") && !k.equals("recipes"))
                    .forEach(k -> replaceItems(json.get(k), conversions, id -> true, k.endsWith("conditions") ? new ArrayList<>() : inputs));
            replaceOutputs(json, outputKeys, conversions);
            toTags(json, inputs, conversions, inputTags, decoder);
            return Optional.empty();
        }
        Optional<Decoded> read = decoder.decode(before).filter(Decoded::exposed);
        for (String key : INPUT_KEYS) {
            if (json.has(key)) replaceItems(json.get(key), conversions, id -> read.map(d -> d.consumes(id) || !d.produces(id)).orElse(true), inputs);
        }
        for (String key : OUTPUT_KEYS) {
            if (json.has(key)) replaceStacks(json.get(key), conversions, id -> read.map(d -> d.produces(id) || !d.consumes(id)).orElse(true));
        }
        toTags(json, inputs, conversions, inputTags, decoder);
        return json.equals(before) ? Optional.empty() : Optional.of(type(before));
    }

    /** Recipe ids this pack overrides; their pre-MNX JSON must be read beneath it (ADR-010). */
    public static List<ResourceLocation> overridden(List<PackContent.Effect> manifest) {
        return manifest.stream().filter(e -> e.kind().equals(REWRITE) || e.kind().equals(DISABLE) || e.kind().equals(ProcessPlanner.DISABLE)
                        || e.kind().equals(PackContent.PLAYER_DISABLE))
                .map(PackContent.Effect::target).toList();
    }

    public static Optional<RecipeFormats.Format> format(JsonObject recipe, RecipeFormats formats) {
        JsonElement type = recipe.get("type");
        if (type == null || !type.isJsonPrimitive()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(type.getAsString());
        return id == null ? Optional.empty() : formats.forType(id);
    }

    /** Every item a recipe produces, in output-key order: the format's keys, else the known output keys (ADR-025). */
    public static List<ResourceLocation> outputIds(JsonObject recipe, RecipeFormats formats) {
        List<ResourceLocation> ids = new ArrayList<>();
        format(recipe, formats).map(RecipeFormats.Format::outputKeys).orElse(OUTPUT_KEYS).forEach(key -> collectIds(recipe.get(key), ids));
        return ids;
    }

    private static ResourceLocation type(JsonObject recipe) {
        return Optional.ofNullable(recipe.get("type")).filter(JsonElement::isJsonPrimitive)
                .map(t -> ResourceLocation.tryParse(t.getAsString())).orElse(ResourceLocation.withDefaultNamespace("unknown"));
    }

    /**
     * ADR-025: an alternative's form tag is a usable input when every member is the same material and it holds the kept
     * item. {@code forms}: per unified form, its tag, kept item and alternatives; {@code tags}: tag members without Material
     * Nexus; {@code materialItems}: every item discovered as that form's material (aliases included).
     */
    public record UnifiedForm(ResourceLocation tag, ResourceLocation kept, List<ResourceLocation> alternatives, java.util.Set<ResourceLocation> materialItems) { }

    public static Map<ResourceLocation, ResourceLocation> inputTags(List<UnifiedForm> forms, Map<ResourceLocation, java.util.Set<ResourceLocation>> tags) {
        Map<ResourceLocation, ResourceLocation> out = new TreeMap<>();
        for (UnifiedForm f : forms) {
            java.util.Set<ResourceLocation> members = tags.get(f.tag());
            if (members == null || !members.contains(f.kept()) || !f.materialItems().containsAll(members)) continue;
            f.alternatives().stream().filter(members::contains).forEach(alt -> out.put(alt, f.tag()));
        }
        return out;
    }

    /**
     * The inputs just set to the kept item become the form's tag, one at a time, when the game still reads the recipe and,
     * if it shows its ingredients, accepts both items there; otherwise they stay the kept item.
     */
    private static void toTags(JsonObject recipe, List<JsonObject> inputs, Map<ResourceLocation, ResourceLocation> conversions,
                               Map<ResourceLocation, ResourceLocation> inputTags, Decoder decoder) {
        if (inputTags.isEmpty()) return;
        Map<ResourceLocation, ResourceLocation> alternativeOf = new HashMap<>();
        conversions.forEach((alt, kept) -> { if (inputTags.containsKey(alt)) alternativeOf.putIfAbsent(kept, alt); });
        for (JsonObject input : inputs) {
            ResourceLocation kept = ResourceLocation.tryParse(input.get("item").getAsString());
            ResourceLocation alt = kept == null ? null : alternativeOf.get(kept);
            if (alt == null) continue;
            input.remove("item");
            input.addProperty("tag", inputTags.get(alt).toString());
            boolean accepted = decoder.decode(recipe).map(d -> !d.exposed() || (d.consumes(kept) && d.consumes(alt))).orElse(false);
            if (!accepted) {
                input.remove("tag");
                input.addProperty("item", kept.toString());
            }
        }
    }

    /** The first namespaced string naming one of {@code ids}, anywhere in the recipe. */
    private static Optional<ResourceLocation> firstMention(JsonElement element, java.util.Set<ResourceLocation> ids) {
        if (element.isJsonPrimitive()) {
            if (!element.getAsJsonPrimitive().isString() || element.getAsString().indexOf(':') <= 0) return Optional.empty();
            return Optional.ofNullable(ResourceLocation.tryParse(element.getAsString())).filter(ids::contains);
        }
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                Optional<ResourceLocation> found = firstMention(e, ids);
                if (found.isPresent()) return found;
            }
        } else if (element.isJsonObject()) {
            for (var e : element.getAsJsonObject().entrySet()) {
                Optional<ResourceLocation> found = firstMention(e.getValue(), ids);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
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

    /** Replaces alternatives in the outputs (a stack, a bare id, or nested in lists and objects such as IE secondaries), in place. */
    private static void replaceOutputs(JsonObject copy, List<String> outputKeys, Map<ResourceLocation, ResourceLocation> conversions) {
        for (String key : outputKeys) {
            JsonElement output = copy.get(key);
            if (output == null) continue;
            if (output.isJsonPrimitive()) {
                ResourceLocation id = ResourceLocation.tryParse(output.getAsString());
                if (id != null && conversions.containsKey(id)) copy.addProperty(key, conversions.get(id).toString());
            } else {
                replaceStacks(output, conversions, id -> true);
            }
        }
    }

    /** Stacks ({@code "id"} or {@code "item"}) naming an alternative {@code allowed} lets through, in lists and nested objects. */
    private static void replaceStacks(JsonElement output, Map<ResourceLocation, ResourceLocation> conversions, java.util.function.Predicate<ResourceLocation> allowed) {
        if (output.isJsonArray()) {
            output.getAsJsonArray().forEach(e -> replaceStacks(e, conversions, allowed));
        } else if (output.isJsonObject()) {
            JsonObject o = output.getAsJsonObject();
            String field = o.has("id") ? "id" : o.has("item") ? "item" : null;
            JsonElement id = field == null ? null : o.get(field);
            if (id != null && id.isJsonPrimitive()) {
                ResourceLocation item = ResourceLocation.tryParse(id.getAsString());
                if (item != null && conversions.containsKey(item) && allowed.test(item)) o.addProperty(field, conversions.get(item).toString());
            } else {
                o.entrySet().forEach(e -> replaceStacks(e.getValue(), conversions, allowed));
            }
        }
    }

    /**
     * Replaces every literal {@code "item": <alternative>} {@code allowed} lets through, in place, and collects the objects
     * changed (candidates for the form's tag); tag ingredients are left as they are.
     */
    private static void replaceItems(JsonElement element, Map<ResourceLocation, ResourceLocation> conversions,
                                     java.util.function.Predicate<ResourceLocation> allowed, List<JsonObject> changed) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> replaceItems(e, conversions, allowed, changed));
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            JsonElement item = o.get("item");
            if (item != null && item.isJsonPrimitive()) {
                ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
                ResourceLocation canonical = id == null ? null : conversions.get(id);
                if (canonical != null && allowed.test(id)) {
                    o.addProperty("item", canonical.toString());
                    changed.add(o);
                }
            }
            o.entrySet().forEach(e -> replaceItems(e.getValue(), conversions, allowed, changed));
        }
    }

    private static final java.util.Set<String> COSMETIC = java.util.Set.of("group", "category", "show_notification");

    /** What makes two recipes the same for a player: everything but grouping and book category. Shares the children: read only. */
    private static JsonElement normalized(JsonObject recipe) {
        JsonObject copy = new JsonObject();
        recipe.entrySet().forEach(e -> { if (!COSMETIC.contains(e.getKey())) copy.add(e.getKey(), e.getValue()); });
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

    static String path(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json";
    }
}
