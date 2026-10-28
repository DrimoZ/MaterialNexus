package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = dev.drimoz.materialnexus.MaterialNexus.MOD_ID)
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
        // ADR-025: any other type naming an alternative, read from its file and rewritten by its keys. A recipe with no
        // file (added in memory by a script, e.g. KubeJS) has no JSON we may rewrite: it is reported, not skipped.
        Map<ResourceLocation, String> files = texts(server);
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            if (ours.contains(holder.getId()) || read.contains(holder.getId())) continue;
            Optional<JsonObject> json = parse(files.get(file(holder.getId()))).filter(j -> mentions(j, conversions.keySet()));
            if (json.isPresent()) {
                sources.add(new RecipeRewrites.Source(holder.getId(), json, firstOrSelf(RecipeRewrites.outputIds(json.get(), formats), holder.getId())));
                continue;
            }
            ItemStack out = holder.getResultItem(server.registryAccess());
            ResourceLocation result = out.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(out.getItem());
            if (result != null && conversions.containsKey(result)) {
                sources.add(new RecipeRewrites.Source(holder.getId(), Optional.empty(), result));
            }
        }
        sources.sort(Comparator.comparing(RecipeRewrites.Source::id));
        return sources;
    }

    /** Every known-format recipe with its pre-MNX JSON, for {@link #collect} and process rules (MNX-036). Preview / apply only. */
    public static List<RecipeRewrites.Source> known(MinecraftServer server, RecipeFormats formats, Collection<ResourceLocation> overridden) {
        Set<ResourceLocation> ids = new java.util.TreeSet<>(overridden);
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.getSerializer());
            if (type != null && formats.forType(type).isPresent()) ids.add(holder.getId());
        }
        // Parsed in parallel (independent texts), in id order. Our own generated recipes have no file beneath the
        // generated pack: never read back here.
        Map<ResourceLocation, String> files = texts(server);
        return ids.parallelStream()
                .flatMap(id -> parse(files.get(file(id))).stream().map(json -> new RecipeRewrites.Source(id, Optional.of(json), id)))
                .toList();
    }

    /** The text cache of one resource manager; the game makes a new one on every reload, Apply included. */
    private record Texts(java.lang.ref.WeakReference<ResourceManager> owner, Map<ResourceLocation, String> files) { }

    private static Texts texts;

    /**
     * MNX-114: the pre-MNX text of the recipe files, read once per reload, on first need (a reload is not slowed down
     * when nobody previews). Text rather than parsed JSON: compact, and nothing can edit it in place. MNX-115/116: only
     * what previews read: loaded recipes of a known format, plus those the generated pack overrides (a disabled one is
     * not loaded). Any other recipe is read from its file when asked ({@link #originalJson}). Dropped when the server stops.
     */
    private static synchronized Map<ResourceLocation, String> texts(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        if (texts == null || texts.owner().get() != resources) {
            long start = System.nanoTime();
            RecipeFormats formats = RecipeFormats.load(resources);
            Set<ResourceLocation> kept = new HashSet<>();
            for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
                ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.getSerializer());
                if (type != null && formats.forType(type).isPresent()) kept.add(file(holder.getId()));
            }
            // ADR-025: of the other types, only the files naming an item that could become an alternative (a form with
            // two items or more): rewritten by their keys once a unification makes it one.
            Set<ResourceLocation> shared = new HashSet<>();
            SnapshotManager.current().discovered().materials().values().forEach(forms -> forms.values().forEach(providers -> {
                if (providers.size() > 1) providers.forEach(p -> shared.add(p.resource()));
            }));
            Map<ResourceLocation, String> files = resources.listResourceStacks("recipes", path -> path.getPath().endsWith(".json"))
                    .entrySet().parallelStream()
                    .flatMap(e -> {
                        boolean known = kept.contains(e.getKey()) || e.getValue().stream().anyMatch(r -> r.sourcePackId().equals(GeneratedPack.PACK_ID));
                        return originalText(e.getValue()).filter(text -> known || ids(text).stream().anyMatch(shared::contains))
                                .stream().map(text -> Map.entry(e.getKey(), text));
                    })
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            texts = new Texts(new java.lang.ref.WeakReference<>(resources), files);
            com.mojang.logging.LogUtils.getLogger().info("Material Nexus read {} recipe files ({} KB of text) in {} ms", files.size(),
                    files.values().stream().mapToLong(String::length).sum() / 1024, (System.nanoTime() - start) / 1_000_000);
        }
        return texts.files();
    }

    /** Leaving a world (or stopping the server) frees the texts; the next server reads its own. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static synchronized void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        texts = null;
    }

    /** ADR-025: for each unified form, the tag its inputs may become, from the current snapshot (tags as they are without Material Nexus). */
    public static Map<ResourceLocation, ResourceLocation> inputTags(java.util.SortedMap<MaterialId, ResolvedMaterial> resolved) {
        var snapshot = SnapshotManager.current();
        List<RecipeRewrites.UnifiedForm> forms = new ArrayList<>();
        resolved.forEach((material, rm) -> {
            Set<ResourceLocation> materialItems = new HashSet<>();
            snapshot.discovered().materials().getOrDefault(material, java.util.Collections.emptySortedMap())
                    .values().forEach(providers -> providers.forEach(p -> materialItems.add(p.resource())));
            rm.forms().forEach((form, f) -> {
                if (!PackContent.isUnified(f)) return;
                TagDiscovery.conventionTag(material, form).ifPresent(tag ->
                        forms.add(new RecipeRewrites.UnifiedForm(tag, f.canonical().orElseThrow(), f.alternatives(), materialItems)));
            });
        });
        return RecipeRewrites.inputTags(forms, snapshot.conventionTags());
    }

    /** The game's reading of a recipe, load conditions left out; Preview / Apply only. */
    public static RecipeRewrites.Decoder decoder(MinecraftServer server) {
        return json -> {
            JsonObject recipe = json.deepCopy();
            recipe.remove("conditions");
            recipe.remove("forge:conditions");
            try {
                var decoded = net.minecraft.world.item.crafting.RecipeManager.fromJson(ResourceLocation.fromNamespaceAndPath("materialnexus", "validation"), recipe);
                ItemStack result = decoded.getResultItem(server.registryAccess());
                var ingredients = decoded.getIngredients();
                return Optional.of(new RecipeRewrites.Decoded() {
                    public boolean exposed() { return !result.isEmpty() || !ingredients.isEmpty(); }
                    public boolean produces(ResourceLocation item) { return !result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(item); }
                    public boolean consumes(ResourceLocation item) {
                        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
                        return ingredients.stream().anyMatch(i -> i.test(stack));
                    }
                });
            } catch (RuntimeException e) {
                // A mod whose recipe cannot be read, or will not tell its result here: the game cannot vouch for it.
                return Optional.empty();
            }
        };
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

    /** MNX-117: the packs providing a recipe's file, lowest first, the generated pack included; empty for a recipe made in memory. */
    public static List<String> packs(MinecraftServer server, ResourceLocation id) {
        return server.getResourceManager().getResourceStack(file(id)).stream().map(Resource::sourcePackId).toList();
    }

    /** True when some pack other than the generated one has the recipe's file: the generated pack can then switch it off. */
    public static boolean removable(List<String> packs) {
        return packs.stream().anyMatch(p -> !p.equals(GeneratedPack.PACK_ID));
    }

    /** MNX-117: of these recipe ids, those the generated pack can switch off, with what each made (the recipe itself when unknown). */
    public static Map<ResourceLocation, ResourceLocation> removable(MinecraftServer server, Collection<ResourceLocation> ids, RecipeFormats formats) {
        Map<ResourceLocation, ResourceLocation> out = new java.util.TreeMap<>();
        for (ResourceLocation id : ids) {
            if (!removable(packs(server, id))) continue;
            out.put(id, firstOrSelf(originalJson(server, id).map(json -> RecipeRewrites.outputIds(json, formats)).orElse(List.of()), id));
        }
        return out;
    }

    /** The highest-priority definition of a recipe, skipping the Material Nexus generated pack. */
    public static Optional<JsonObject> originalJson(MinecraftServer server, ResourceLocation id) {
        String text = texts(server).get(file(id));
        // Outside the cache (another format, only /materials report asks): read from the file, not kept.
        return parse(text != null ? text : originalText(server.getResourceManager().getResourceStack(file(id))).orElse(null));
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
