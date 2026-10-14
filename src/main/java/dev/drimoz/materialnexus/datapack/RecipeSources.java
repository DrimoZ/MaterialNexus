package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server adapter for {@link RecipeRewrites}: recipes touching unified items, with their JSON as it was
 * before Material Nexus (ADR-010). Called on preview / apply only, never per tick.
 */
public final class RecipeSources {
    /** Any quoted namespaced id in a recipe's JSON text. */
    private static final java.util.regex.Pattern QUOTED_ID = java.util.regex.Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private RecipeSources() { }

    /** Every quoted namespaced id in a recipe's JSON text (MNX-076: what a script edit took out or put in). */
    public static Set<ResourceLocation> ids(String text) {
        Set<ResourceLocation> ids = new HashSet<>();
        var m = QUOTED_ID.matcher(text);
        while (m.find()) {
            ResourceLocation id = ResourceLocation.tryParse(m.group(1));
            if (id != null) ids.add(id);
        }
        return ids;
    }

    /** Any string value naming one of {@code ids}, in one walk of the tree (MNX-044: 178 alternatives × 13k recipes). */
    private static boolean mentions(JsonElement element, Set<ResourceLocation> ids) {
        if (element.isJsonPrimitive()) {
            if (!element.getAsJsonPrimitive().isString()) return false;
            String text = element.getAsString();
            // Namespaced only: a bare word ("misc") would parse as minecraft:misc.
            ResourceLocation id = text.indexOf(':') > 0 ? ResourceLocation.tryParse(text) : null;
            return id != null && ids.contains(id);
        }
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) if (mentions(e, ids)) return true;
        } else if (element.isJsonObject()) {
            for (var e : element.getAsJsonObject().entrySet()) if (mentions(e.getValue(), ids)) return true;
        }
        return false;
    }

    public static List<RecipeRewrites.Source> collect(MinecraftServer server, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                                      Collection<ResourceLocation> overridden) {
        return collect(server, conversions, formats, overridden, known(server, formats, overridden));
    }

    /**
     * Known-format recipes producing or literally consuming an alternative or canonical item, read from
     * their JSON (Mekanism does not expose its outputs through vanilla APIs); unknown-format recipes
     * producing an alternative, to be listed as unsupported. {@code conversions} maps alternative to canonical.
     * {@code known} is {@link #known} for the same {@code overridden}: read once, shared with process rules (MNX-111).
     */
    public static List<RecipeRewrites.Source> collect(MinecraftServer server, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                                      Collection<ResourceLocation> overridden, List<RecipeRewrites.Source> known) {
        Set<ResourceLocation> ours = new HashSet<>(overridden);
        Set<ResourceLocation> items = new HashSet<>(conversions.keySet());
        items.addAll(conversions.values());
        Set<ResourceLocation> read = new HashSet<>();
        List<RecipeRewrites.Source> sources = new ArrayList<>();

        for (RecipeRewrites.Source s : known) {
            read.add(s.id());
            JsonObject json = s.json().orElseThrow();
            List<ResourceLocation> outputs = RecipeRewrites.outputIds(json, formats);
            // Recipes we rewrote or disabled are judged by their original JSON, not by what we made of them.
            boolean touches = ours.contains(s.id()) || outputs.stream().anyMatch(items::contains) || mentions(json, conversions.keySet());
            if (touches) sources.add(new RecipeRewrites.Source(s.id(), s.json(), firstOrSelf(outputs, s.id())));
        }
        // A recipe with no file (added in memory by a script, e.g. KubeJS) has no JSON we may rewrite: it is
        // reported like an unknown type rather than silently skipped.
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            if (ours.contains(holder.getId()) || read.contains(holder.getId())) continue;
            ItemStack out = holder.getResultItem(server.registryAccess());
            ResourceLocation result = out.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(out.getItem());
            if (result != null && conversions.containsKey(result)) {
                sources.add(new RecipeRewrites.Source(holder.getId(), Optional.empty(), result));
            }
        }
        sources.sort(Comparator.comparing(RecipeRewrites.Source::id));
        return sources;
    }

    /**
     * Every known-format recipe with its pre-MNX JSON, for {@link #collect} and process rules (MNX-036). Preview / apply only.
     * ponytail: reads every file on each preview (24.5k recipes on the 1.20.1 dev pack, MNX-111); index on reload if that
     * becomes the bottleneck.
     */
    public static List<RecipeRewrites.Source> known(MinecraftServer server, RecipeFormats formats, Collection<ResourceLocation> overridden) {
        ResourceManager resources = server.getResourceManager();
        Set<ResourceLocation> ids = new java.util.TreeSet<>(overridden);
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.getSerializer());
            if (type != null && formats.forType(type).isPresent()) ids.add(holder.getId());
        }
        // MNX-111: one listing instead of one lookup per recipe; files are independent, so they are read and parsed in
        // parallel, in id order. Our own generated recipes have no file beneath the generated pack: never read back here.
        Map<ResourceLocation, List<Resource>> files = resources.listResourceStacks("recipes", path -> path.getPath().endsWith(".json"));
        return ids.parallelStream()
                .flatMap(id -> originalJson(files.getOrDefault(file(id), List.of())).stream().map(json -> new RecipeRewrites.Source(id, Optional.of(json), id)))
                .toList();
    }

    private static ResourceLocation firstOrSelf(List<ResourceLocation> outputs, ResourceLocation recipe) {
        return outputs.isEmpty() ? recipe : outputs.get(0);
    }

    /**
     * Forge 1.20.1 port: why the game cannot read this recipe JSON, or empty when it can (1.21 decodes with Recipe.CODEC).
     * Load conditions are not evaluated here; callers strip them first.
     */
    public static Optional<String> decodeError(JsonObject json) {
        try {
            net.minecraft.world.item.crafting.RecipeManager.fromJson(ResourceLocation.fromNamespaceAndPath("materialnexus", "validation"), json);
            return Optional.empty();
        } catch (RuntimeException e) {
            return Optional.of(String.valueOf(e.getMessage()));
        }
    }

    private static ResourceLocation file(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipes/" + id.getPath() + ".json");
    }

    /** The highest-priority definition of a recipe, skipping the Material Nexus generated pack. */
    public static Optional<JsonObject> originalJson(ResourceManager resources, ResourceLocation id) {
        return originalJson(resources.getResourceStack(file(id)));
    }

    private static Optional<JsonObject> originalJson(List<Resource> stack) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            if (resource.sourcePackId().equals(GeneratedPack.PACK_ID)) continue;
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
