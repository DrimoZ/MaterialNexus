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
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (ours.contains(holder.id()) || read.contains(holder.id())) continue;
            ItemStack out = holder.value().getResultItem(server.registryAccess());
            ResourceLocation result = out.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(out.getItem());
            if (result != null && conversions.containsKey(result)) {
                sources.add(new RecipeRewrites.Source(holder.id(), Optional.empty(), result));
            }
        }
        sources.sort(Comparator.comparing(RecipeRewrites.Source::id));
        return sources;
    }

    /** Every known-format recipe with its pre-MNX JSON, for {@link #collect} and process rules (MNX-036). Preview / apply only. */
    public static List<RecipeRewrites.Source> known(MinecraftServer server, RecipeFormats formats, Collection<ResourceLocation> overridden) {
        Set<ResourceLocation> ids = new java.util.TreeSet<>(overridden);
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            if (type != null && formats.forType(type).isPresent()) ids.add(holder.id());
        }
        // Parsed in parallel (independent texts), in id order. Our own generated recipes have no file beneath the
        // generated pack: never read back here.
        Map<ResourceLocation, String> files = texts(server.getResourceManager());
        return ids.parallelStream()
                .flatMap(id -> parse(files.get(file(id))).stream().map(json -> new RecipeRewrites.Source(id, Optional.of(json), id)))
                .toList();
    }

    /** The text cache of one resource manager; the game makes a new one on every reload, Apply included. */
    private record Texts(java.lang.ref.WeakReference<ResourceManager> owner, Map<ResourceLocation, String> files) { }

    private static Texts texts;

    /**
     * MNX-114: the pre-MNX text of every recipe file, read once per reload, on first need (a reload is not slowed down
     * when nobody previews). Text rather than parsed JSON: compact (about 4 MB for 13k recipes) and nothing can edit it
     * in place. ponytail: the last pack's texts stay in memory until the next preview, even after leaving the world.
     */
    private static synchronized Map<ResourceLocation, String> texts(ResourceManager resources) {
        if (texts == null || texts.owner().get() != resources) {
            long start = System.nanoTime();
            Map<ResourceLocation, String> files = resources.listResourceStacks("recipe", path -> path.getPath().endsWith(".json"))
                    .entrySet().parallelStream()
                    .flatMap(e -> originalText(e.getValue()).stream().map(text -> Map.entry(e.getKey(), text)))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            texts = new Texts(new java.lang.ref.WeakReference<>(resources), files);
            com.mojang.logging.LogUtils.getLogger().info("Material Nexus read {} recipe files in {} ms", files.size(), (System.nanoTime() - start) / 1_000_000);
        }
        return texts.files();
    }

    private static ResourceLocation firstOrSelf(List<ResourceLocation> outputs, ResourceLocation recipe) {
        return outputs.isEmpty() ? recipe : outputs.getFirst();
    }

    private static ResourceLocation file(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
    }

    /** The highest-priority definition of a recipe, skipping the Material Nexus generated pack. */
    public static Optional<JsonObject> originalJson(ResourceManager resources, ResourceLocation id) {
        return parse(texts(resources).get(file(id)));
    }

    /** The text of the top definition below the generated pack; empty when it cannot be read (no lower one is tried). */
    private static Optional<String> originalText(List<Resource> stack) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            if (resource.sourcePackId().equals(GeneratedPack.PACK_ID)) continue;
            try (var in = resource.open()) {
                return Optional.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonObject> parse(String text) {
        if (text == null) return Optional.empty();
        try {
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
