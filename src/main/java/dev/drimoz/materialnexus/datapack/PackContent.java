package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
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
    /** Alternatives the server swaps for the canonical item when the game touches them (MNX-028). */
    public static final String ITEM_CONVERSION = "item_conversion";
    /** Preview only: an item created for a missing form (MNX-039), {@code target} the item, {@code item} the tag it joins. */
    public static final String ITEM_CREATE = "item_create";
    /** A generated or rewritten recipe the game could not decode (MNX-037): not written, the original recipe stays. */
    public static final String RECIPE_INVALID = "recipe_invalid";
    /** Informational: a domain left to Almost Unified ({@code target} = materialnexus:&lt;domain&gt;). Generates no file. */
    public static final String ALMOST_UNIFIED = "almost_unified";
    /** Preview only, never written (MNX-076): target, the item scripts keep; item, the one Material Nexus would keep. */
    public static final String SCRIPT_CONFLICT = "script_conflict";
    private static final ResourceLocation AU_ID = ResourceLocation.fromNamespaceAndPath(AlmostUnified.MOD_ID, "owner");

    /** One generated change; for both conversions, {@code target} is the canonical item. */
    public record Effect(String kind, ResourceLocation target, ResourceLocation item) { }

    public record Content(Map<String, JsonElement> files, List<Effect> effects) { }

    private static final Comparator<Effect> ORDER = Comparator.comparing(Effect::kind)
            .thenComparing(Effect::target).thenComparing(Effect::item);

    private PackContent() { }

    /** Alternative to canonical item, from the applied pack only: nothing converts before an apply. */
    public static Map<ResourceLocation, ResourceLocation> itemConversions(List<Effect> effects) {
        Map<ResourceLocation, ResourceLocation> conversions = new TreeMap<>();
        for (Effect e : effects) if (e.kind().equals(ITEM_CONVERSION)) conversions.put(e.item(), e.target());
        return conversions;
    }

    /** Recipe side of {@link #full}: given the conversions and what Material Nexus may do, the recipe content. */
    public interface RecipePlanner {
        Content plan(Map<ResourceLocation, ResourceLocation> conversions, AlmostUnified.Ownership ownership);
    }

    /**
     * Tag/item content plus the recipe content it implies; the generated pack is exactly this. With Almost
     * Unified installed, domains left to it generate nothing and are noted (ADR-012).
     */
    public static Content full(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, boolean auPresent, RecipePlanner recipes) {
        AlmostUnified.Ownership ownership = policy.almostUnified().ownership(auPresent);
        Content base = generate(resolved, policy, ownership);
        List<Effect> effects = new ArrayList<>(base.effects());
        Map<String, JsonElement> files = new TreeMap<>(base.files());
        if (auPresent) {
            for (AlmostUnified.Domain d : AlmostUnified.Domain.values()) {
                if (!policy.almostUnified().mnxOwns(d, true)) effects.add(new Effect(ALMOST_UNIFIED, ResourceLocation.fromNamespaceAndPath("materialnexus", d.key), AU_ID));
            }
        }
        Map<ResourceLocation, ResourceLocation> conversions = itemConversions(base.effects());
        if (!conversions.isEmpty()) {
            Content recipe = recipes.plan(conversions, ownership);
            files.putAll(recipe.files());
            effects.addAll(recipe.effects());
        }
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /**
     * Process recipes (MNX-036) on top of the unification content. A recipe the rules disable is disabled even if
     * unification rewrote it: the explicit process decision is the more specific one.
     */
    public static Content withProcesses(Content base, Content processes) {
        Map<String, JsonElement> files = new TreeMap<>(base.files());
        files.putAll(processes.files());
        java.util.Set<ResourceLocation> disabled = new java.util.HashSet<>();
        processes.effects().stream().filter(e -> e.kind().equals(ProcessPlanner.DISABLE)).forEach(e -> disabled.add(e.target()));
        List<Effect> effects = new ArrayList<>(base.effects().stream()
                .filter(e -> !(disabled.contains(e.target()) && (e.kind().equals(RecipeRewrites.REWRITE) || e.kind().equals(RecipeRewrites.DISABLE))))
                .toList());
        effects.addAll(processes.effects());
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /**
     * Every recipe file this pack would write is decoded by the game first ({@code decodes}); one that fails is dropped
     * and reported, so a wrong template or format never reaches the world. Disabling stubs have no type and pass.
     */
    public static Content withValidRecipes(Content content, java.util.function.Predicate<JsonObject> decodes) {
        Map<String, JsonElement> files = new TreeMap<>(content.files());
        java.util.Set<ResourceLocation> invalid = new java.util.HashSet<>();
        for (var e : content.files().entrySet()) {
            String[] parts = e.getKey().split("/", 4);
            if (parts.length < 4 || !parts[0].equals("data") || !parts[2].equals("recipes") || !(e.getValue() instanceof JsonObject json) || !json.has("type")) continue;
            JsonObject recipe = json.deepCopy();
            recipe.remove("conditions");
            if (decodes.test(recipe)) continue;
            files.remove(e.getKey());
            invalid.add(ResourceLocation.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - ".json".length())));
        }
        if (invalid.isEmpty()) return content;
        List<Effect> effects = new ArrayList<>();
        for (Effect e : content.effects()) {
            boolean dropped = invalid.contains(e.target()) && (e.kind().equals(RecipeRewrites.REWRITE) || e.kind().equals(ProcessPlanner.PROCESS));
            effects.add(dropped ? new Effect(RECIPE_INVALID, e.target(), e.item()) : e);
        }
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    public static boolean isUnified(ResolvedForm form) {
        return form.canonical().isPresent() && form.source() != PolicyPrecedence.DEFAULT && !form.alternatives().isEmpty();
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy) {
        return generate(resolved, policy, AlmostUnified.Ownership.ALL);
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, AlmostUnified.Ownership ownership) {
        SortedMap<ResourceLocation, TreeSet<ResourceLocation>> removals = new TreeMap<>();
        Map<String, JsonElement> files = new TreeMap<>();
        List<Effect> effects = new ArrayList<>();
        resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
            if (!isUnified(f)) return;
            ResourceLocation canonical = f.canonical().orElseThrow();
            for (ResourceLocation alt : f.alternatives()) effects.add(new Effect(ITEM_CONVERSION, canonical, alt));
            if (ownership.tags()) {
                TagDiscovery.conventionTag(material, form)
                        .ifPresent(tag -> removals.computeIfAbsent(tag, t -> new TreeSet<>()).addAll(f.alternatives()));
            }
            if (policy.conversionRecipeForms().contains(form)) {
                for (ResourceLocation alt : f.alternatives()) {
                    String id = "convert/" + material.name() + "/" + form.name() + "/" + alt.getNamespace() + "_" + alt.getPath().replace('/', '_');
                    files.put("data/materialnexus/recipes/" + id + ".json", conversionRecipe(alt, canonical));
                    effects.add(new Effect(CONVERSION, canonical, alt));
                }
            }
        }));
        removals.forEach((tag, items) -> {
            files.put("data/" + tag.getNamespace() + "/tags/items/" + tag.getPath() + ".json", tagRemoval(items));
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
        result.addProperty("item", to.toString());
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
