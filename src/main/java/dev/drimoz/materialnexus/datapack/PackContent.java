package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the generated pack contains for a resolved policy (ADR-007). Only forms unified by a
 * decision of the player (any policy level, never the default) produce anything: alternatives
 * are removed from the material tag, and listed forms get a 1:1 conversion recipe.
 */
public final class PackContent {
    public static final String TAG_REMOVE = "tag_remove";
    public static final String CONVERSION = "conversion_recipe";

    /** One generated change; for a conversion, {@code target} is the canonical item it produces. */
    public record Effect(String kind, ResourceLocation target, ResourceLocation item) { }

    public record Content(Map<String, JsonElement> files, List<Effect> effects) { }

    private static final Comparator<Effect> ORDER = Comparator.comparing(Effect::kind)
            .thenComparing(Effect::target).thenComparing(Effect::item);

    private PackContent() { }

    public static boolean isUnified(ResolvedForm form) {
        return form.canonical().isPresent() && form.source() != PolicyPrecedence.DEFAULT && !form.alternatives().isEmpty();
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy) {
        SortedMap<ResourceLocation, TreeSet<ResourceLocation>> removals = new TreeMap<>();
        Map<String, JsonElement> files = new TreeMap<>();
        List<Effect> effects = new ArrayList<>();
        resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
            if (!isUnified(f)) return;
            ResourceLocation canonical = f.canonical().orElseThrow();
            TagDiscovery.conventionTag(material, form)
                    .ifPresent(tag -> removals.computeIfAbsent(tag, t -> new TreeSet<>()).addAll(f.alternatives()));
            if (policy.conversionRecipeForms().contains(form)) {
                for (ResourceLocation alt : f.alternatives()) {
                    String id = "convert/" + material.name() + "/" + form.name() + "/" + alt.getNamespace() + "_" + alt.getPath().replace('/', '_');
                    files.put("data/materialnexus/recipe/" + id + ".json", conversionRecipe(alt, canonical));
                    effects.add(new Effect(CONVERSION, canonical, alt));
                }
            }
        }));
        removals.forEach((tag, items) -> {
            files.put("data/" + tag.getNamespace() + "/tags/item/" + tag.getPath() + ".json", tagRemoval(items));
            items.forEach(item -> effects.add(new Effect(TAG_REMOVE, tag, item)));
        });
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /** NeoForge tag file that only removes: the item keeps every other tag it has. */
    private static JsonObject tagRemoval(Collection<ResourceLocation> items) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", new JsonArray());
        JsonArray remove = new JsonArray();
        items.forEach(i -> remove.add(i.toString()));
        tag.add("remove", remove);
        return tag;
    }

    private static JsonObject conversionRecipe(ResourceLocation from, ResourceLocation to) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", from.toString());
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient);
        JsonObject result = new JsonObject();
        result.addProperty("count", 1);
        result.addProperty("id", to.toString());
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        recipe.addProperty("group", "materialnexus_conversion");
        recipe.add("ingredients", ingredients);
        recipe.add("result", result);
        return recipe;
    }

    public static JsonArray toJson(List<Effect> effects) {
        JsonArray array = new JsonArray();
        for (Effect e : effects) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", e.kind());
            o.addProperty("target", e.target().toString());
            o.addProperty("item", e.item().toString());
            array.add(o);
        }
        return array;
    }

    /** Effects recorded in the current generated pack; empty when there is none yet. */
    public static List<Effect> readManifest(Path generatedDir) throws IOException {
        Path manifest = generatedDir.resolve(GeneratedPack.MANIFEST);
        if (!Files.isRegularFile(manifest)) return List.of();
        JsonObject root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Effect> effects = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("changes")) {
            JsonObject o = el.getAsJsonObject();
            effects.add(new Effect(o.get("kind").getAsString(),
                    ResourceLocation.parse(o.get("target").getAsString()), ResourceLocation.parse(o.get("item").getAsString())));
        }
        return effects;
    }

    /**
     * ADR-010 for tags: put back the members Material Nexus itself removed, so discovery keeps
     * seeing the pre-MNX state and the next apply regenerates the same removals.
     */
    public static void restoreRemovedMembers(Map<ResourceLocation, List<ResourceLocation>> tagMembers, List<Effect> effects) {
        for (Effect e : effects) {
            if (!e.kind().equals(TAG_REMOVE)) continue;
            List<ResourceLocation> members = new ArrayList<>(tagMembers.getOrDefault(e.target(), List.of()));
            if (!members.contains(e.item())) members.add(e.item());
            tagMembers.put(e.target(), members);
        }
    }
}
