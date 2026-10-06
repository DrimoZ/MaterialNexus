package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server adapter for {@link RecipeRewrites}: the recipes producing items of interest, with their JSON as
 * it was before Material Nexus (ADR-010). Called on preview / apply only, never per tick.
 */
public final class RecipeSources {
    /** Vanilla recipe types whose JSON result is a known item stack; anything else is unsupported for now. */
    public static final Set<ResourceLocation> VANILLA_TYPES = Set.of(
            "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
            "campfire_cooking", "stonecutting", "smithing_transform").stream()
            .map(ResourceLocation::withDefaultNamespace).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private RecipeSources() { }

    public static List<RecipeRewrites.Source> collect(MinecraftServer server, Set<ResourceLocation> results, Collection<ResourceLocation> overridden) {
        ResourceManager resources = server.getResourceManager();
        Set<ResourceLocation> ours = new HashSet<>(overridden);
        List<RecipeRewrites.Source> sources = new ArrayList<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (ours.contains(holder.id())) continue;
            ItemStack out = holder.value().getResultItem(server.registryAccess());
            if (out.isEmpty()) continue;
            ResourceLocation result = BuiltInRegistries.ITEM.getKey(out.getItem());
            if (!results.contains(result)) continue;
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            Optional<JsonObject> json = VANILLA_TYPES.contains(type) ? originalJson(resources, holder.id()) : Optional.empty();
            sources.add(new RecipeRewrites.Source(holder.id(), json, result));
        }
        // Recipes we rewrote or disabled are judged by their original JSON, not by what we made of them.
        for (ResourceLocation id : ours) {
            originalJson(resources, id).ifPresent(json -> RecipeRewrites.resultId(json).filter(results::contains)
                    .ifPresent(result -> sources.add(new RecipeRewrites.Source(id, Optional.of(json), result))));
        }
        sources.sort(Comparator.comparing(RecipeRewrites.Source::id));
        return sources;
    }

    /** The highest-priority definition of a recipe, skipping the Material Nexus generated pack. */
    static Optional<JsonObject> originalJson(ResourceManager resources, ResourceLocation id) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
        List<Resource> stack = resources.getResourceStack(file);
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
