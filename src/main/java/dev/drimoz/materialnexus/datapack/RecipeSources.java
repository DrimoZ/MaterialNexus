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
import net.minecraft.world.item.crafting.RecipeHolder;

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
    private RecipeSources() { }

    /**
     * Known-format recipes producing or literally consuming an alternative or canonical item, read from
     * their JSON (Mekanism does not expose its outputs through vanilla APIs); unknown-format recipes
     * producing an alternative, to be listed as unsupported. {@code conversions} maps alternative to canonical.
     */
    public static List<RecipeRewrites.Source> collect(MinecraftServer server, Map<ResourceLocation, ResourceLocation> conversions, RecipeFormats formats,
                                                      Collection<ResourceLocation> overridden) {
        ResourceManager resources = server.getResourceManager();
        Set<ResourceLocation> ours = new HashSet<>(overridden);
        Set<ResourceLocation> items = new HashSet<>(conversions.keySet());
        items.addAll(conversions.values());
        List<String> alternativeIds = conversions.keySet().stream().map(id -> "\"" + id + "\"").toList();
        List<RecipeRewrites.Source> sources = new ArrayList<>();

        // ponytail: reads the JSON of every known-format recipe on each preview (a few thousand small files,
        // explicit player action only). Index by output on reload if this ever shows up in a profile.
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (ours.contains(holder.id())) continue;
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            // A recipe with no file (added in memory by a script, e.g. KubeJS) has no JSON we may rewrite: it is
            // reported like an unknown type rather than silently skipped.
            Optional<JsonObject> file = type != null && formats.forType(type).isPresent() ? originalJson(resources, holder.id()) : Optional.empty();
            if (file.isPresent()) {
                JsonObject json = file.get();
                List<ResourceLocation> outputs = RecipeRewrites.outputIds(json, formats);
                String text = json.toString();
                boolean touches = outputs.stream().anyMatch(items::contains) || alternativeIds.stream().anyMatch(text::contains);
                if (touches) sources.add(new RecipeRewrites.Source(holder.id(), Optional.of(json), firstOrSelf(outputs, holder.id())));
            } else {
                ItemStack out = holder.value().getResultItem(server.registryAccess());
                ResourceLocation result = out.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(out.getItem());
                if (result != null && conversions.containsKey(result)) {
                    sources.add(new RecipeRewrites.Source(holder.id(), Optional.empty(), result));
                }
            }
        }
        // Recipes we rewrote or disabled are judged by their original JSON, not by what we made of them.
        for (ResourceLocation id : ours) {
            originalJson(resources, id).ifPresent(json ->
                    sources.add(new RecipeRewrites.Source(id, Optional.of(json), firstOrSelf(RecipeRewrites.outputIds(json, formats), id))));
        }
        sources.sort(Comparator.comparing(RecipeRewrites.Source::id));
        return sources;
    }

    /** Every known-format recipe with its pre-MNX JSON, for process rules (MNX-036). Preview / apply only. */
    public static List<RecipeRewrites.Source> known(MinecraftServer server, RecipeFormats formats, Collection<ResourceLocation> overridden) {
        ResourceManager resources = server.getResourceManager();
        Set<ResourceLocation> ids = new java.util.TreeSet<>(overridden);
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            if (type != null && formats.forType(type).isPresent()) ids.add(holder.id());
        }
        List<RecipeRewrites.Source> sources = new ArrayList<>();
        // Our own generated recipes have no file beneath the generated pack, so they are never read back here.
        for (ResourceLocation id : ids) {
            originalJson(resources, id).ifPresent(json -> sources.add(new RecipeRewrites.Source(id, Optional.of(json), id)));
        }
        return sources;
    }

    private static ResourceLocation firstOrSelf(List<ResourceLocation> outputs, ResourceLocation recipe) {
        return outputs.isEmpty() ? recipe : outputs.getFirst();
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
