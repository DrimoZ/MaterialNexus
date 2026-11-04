package dev.drimoz.materialnexus.datapack;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server adapter for {@link FamilyRelations}: the loaded recipes producing one material's items, reduced to
 * edges. Called when the detail of a material is requested, never per tick. Recipes that do not expose
 * their result through vanilla APIs simply do not count as evidence, so a proposal may be conservative.
 */
public final class RecipeEdges {
    private RecipeEdges() { }

    /** Missing relations for every material at once: the recipes are scanned a single time. */
    public static Map<MaterialId, List<FamilyRelations.Relation>> missingAll(MinecraftServer server, DiscoveredMaterials discovered) {
        Set<Identifier> all = new HashSet<>();
        discovered.materials().values().forEach(forms -> forms.values().forEach(list -> list.forEach(p -> all.add(p.resource()))));
        List<FamilyRelations.Edge> edges = edges(server, all);
        Map<MaterialId, List<FamilyRelations.Relation>> result = new java.util.TreeMap<>();
        discovered.materials().forEach((material, forms) -> {
            Map<FormId, Set<Identifier>> providers = new HashMap<>();
            forms.forEach((form, list) -> providers.put(form, list.stream().map(Provider::resource).collect(Collectors.toSet())));
            result.put(material, FamilyRelations.missing(providers, edges));
        });
        return result;
    }

    public static List<FamilyRelations.Relation> missing(MinecraftServer server, DiscoveredMaterials discovered, MaterialId material) {
        return missing(edges(server, Set.of()), discovered, material);
    }

    /** How many recipes make an item, and how many accept it as an input (MNX-052: what to keep). */
    public record Usage(int produced, int used) { }

    public static Map<Identifier, Usage> usage(List<FamilyRelations.Edge> edges, Set<Identifier> items) {
        Map<Identifier, int[]> counts = new HashMap<>();
        for (Identifier item : items) counts.put(item, new int[2]);
        for (FamilyRelations.Edge edge : edges) {
            int[] made = counts.get(edge.result());
            if (made != null) made[0]++;
            Set<Identifier> seen = new HashSet<>();
            for (Set<Identifier> ingredient : edge.ingredients()) {
                for (Identifier item : ingredient) {
                    int[] used = counts.get(item);
                    if (used != null && seen.add(item)) used[1]++;
                }
            }
        }
        Map<Identifier, Usage> result = new HashMap<>();
        counts.forEach((item, c) -> result.put(item, new Usage(c[0], c[1])));
        return result;
    }

    /** Every recipe as an edge, read once for a GUI request. */
    public static List<FamilyRelations.Edge> edges(MinecraftServer server) {
        return edges(server, Set.of());
    }

    public static List<FamilyRelations.Relation> missing(List<FamilyRelations.Edge> edges, DiscoveredMaterials discovered, MaterialId material) {
        var forms = discovered.materials().get(material);
        if (forms == null) return List.of();
        Map<FormId, Set<Identifier>> providers = new HashMap<>();
        Set<Identifier> all = new HashSet<>();
        forms.forEach((form, list) -> {
            Set<Identifier> ids = list.stream().map(Provider::resource).collect(Collectors.toSet());
            providers.put(form, ids);
            all.addAll(ids);
        });

        return FamilyRelations.missing(providers, edges);
    }

    private static List<FamilyRelations.Edge> edges(MinecraftServer server, Set<Identifier> all) {
        List<FamilyRelations.Edge> edges = new ArrayList<>();
        dev.drimoz.materialnexus.integration.RecipeFormats formats = dev.drimoz.materialnexus.integration.RecipeFormats.load(server.getResourceManager());
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ItemStack out = RecipeSources.result(server, holder.value());
            if (out.isEmpty() || RecipeSources.ingredients(holder.value()).isEmpty()) {
                // Machine recipes (MI, Mekanism...) often hide their result or ingredients from vanilla APIs: read their JSON.
                // ponytail: reads those files on each request; index them on reload if this shows up in a profile.
                Identifier type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
                if (type != null && formats.forType(type).isPresent()) {
                    RecipeSources.originalJson(server, holder.id().identifier()).ifPresent(json -> edges.addAll(fromJson(json, formats)));
                }
                if (out.isEmpty()) continue;
            }
            Identifier result = BuiltInRegistries.ITEM.getKey(out.getItem());
            List<Set<Identifier>> ingredients = new ArrayList<>();
            for (Ingredient ingredient : RecipeSources.ingredients(holder.value())) {
                ingredients.add(ingredient.items().map(h -> BuiltInRegistries.ITEM.getKey(h.value())).collect(Collectors.toSet()));
            }
            edges.add(new FamilyRelations.Edge(result, ingredients));
        }
        return edges;
    }

    /** A recipe known through its format: its first produced item of interest, and every item its inputs accept. */
    // One edge per produced item, any output (intermediates such as MI hot ingots are chain links), tag outputs included.
    private static List<FamilyRelations.Edge> fromJson(com.google.gson.JsonObject json, dev.drimoz.materialnexus.integration.RecipeFormats formats) {
        var format = RecipeRewrites.format(json, formats);
        if (format.isEmpty()) return List.of();
        Set<Identifier> accepted = new HashSet<>();
        Set<Identifier> produced = new HashSet<>();
        json.entrySet().forEach(e -> {
            boolean output = format.get().outputKeys().contains(e.getKey());
            collectInputs(e.getValue(), output ? produced : accepted, output);
        });
        return produced.stream().map(result -> new FamilyRelations.Edge(result, List.of(accepted))).toList();
    }

    private static void collectInputs(com.google.gson.JsonElement element, Set<Identifier> accepted, boolean output) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> collectInputs(e, accepted, output));
        } else if (element.isJsonObject()) {
            var o = element.getAsJsonObject();
            if (o.has("item") && o.get("item").isJsonPrimitive()) java.util.Optional.ofNullable(Identifier.tryParse(o.get("item").getAsString())).ifPresent(accepted::add);
            if (o.has("tag") && o.get("tag").isJsonPrimitive()) {
                Identifier tag = Identifier.tryParse(o.get("tag").getAsString());
                if (tag != null) BuiltInRegistries.ITEM.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))
                        .ifPresent(set -> set.forEach(h -> h.unwrapKey().ifPresent(k -> accepted.add(k.identifier()))));
            }
            // Outputs name items with "id" (Create, Mekanism); in inputs "id" may name a fluid or chemical, so it is ignored there.
            if (output && o.has("id") && o.get("id").isJsonPrimitive()) java.util.Optional.ofNullable(Identifier.tryParse(o.get("id").getAsString())).ifPresent(accepted::add);
            o.entrySet().forEach(e -> collectInputs(e.getValue(), accepted, output));
        }
    }
}
