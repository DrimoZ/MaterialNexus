package dev.drimoz.materialnexus.integration;

import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where scripts' changes are read from at each data load (MNX-076, docs/20), with no script mod class: the item tags as
 * the data files define them, and the recipe edits the
 * KubeJS plugin hands over. Both are taken once by discovery, after the reload.
 */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class ScriptSources extends SimplePreparableReloadListener<Map<ResourceLocation, Collection<ResourceLocation>>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, Collection<ResourceLocation>> fileTags = Map.of();
    private static volatile Supplier<List<ScriptChanges.RecipeEdit>> recipes;

    private ScriptSources() { }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new ScriptSources());
    }

    @Override
    protected Map<ResourceLocation, Collection<ResourceLocation>> prepare(ResourceManager resources, ProfilerFiller profiler) {
        // ponytail: reads every item tag file a second time per reload (tens of ms); only on reload, never per tick.
        return new TagLoader<ResourceLocation>(id -> BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(id) : Optional.empty(), TAGS)
                .build(load(resources));
    }

    private static final String TAGS = "tags/items";

    /**
     * TagLoader.load as Forge 1.20.1 does it (replace, Forge remove entries), without calling it: KubeJS 2001 hooks that
     * method and, even for a loader of its own, resets its shared tag context first, which recipe scripts of the same
     * reload read. A file that does not parse is skipped here; the game reports it from its own load.
     */
    private static Map<ResourceLocation, List<TagLoader.EntryWithSource>> load(ResourceManager resources) {
        FileToIdConverter files = FileToIdConverter.json(TAGS);
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags = new HashMap<>();
        files.listMatchingResourceStacks(resources).forEach((file, stack) -> {
            ResourceLocation id = files.fileToId(file);
            for (var resource : stack) {
                try (var reader = resource.openAsReader()) {
                    var parsed = TagFile.CODEC.parse(new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,
                            com.google.gson.JsonParser.parseReader(reader))).result();
                    if (parsed.isEmpty()) continue;
                    List<TagLoader.EntryWithSource> entries = tags.computeIfAbsent(id, k -> new ArrayList<>());
                    if (parsed.get().replace()) entries.clear();
                    parsed.get().entries().forEach(e -> entries.add(new TagLoader.EntryWithSource(e, resource.sourcePackId())));
                    parsed.get().remove().forEach(e -> entries.add(new TagLoader.EntryWithSource(e, resource.sourcePackId(), true)));
                } catch (java.io.IOException | RuntimeException e) {
                    // Reported by the game's own load.
                }
            }
        });
        return tags;
    }

    @Override
    protected void apply(Map<ResourceLocation, Collection<ResourceLocation>> tags, ResourceManager resources, ProfilerFiller profiler) {
        fileTags = Map.copyOf(tags);
    }

    /** Item tags as the data files of the last load define them, before any script. */
    public static Map<ResourceLocation, Collection<ResourceLocation>> fileTags() {
        return fileTags;
    }

    /**
     * MNX-077: KubeJS server scripts as {@code path relative to server_scripts -> lines}, to say where a decision is
     * written. Read only when there are decisions; empty without the folder. An unreadable file is skipped.
     */
    public static Map<String, List<String>> serverScripts() {
        java.nio.file.Path root = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts");
        if (!java.nio.file.Files.isDirectory(root)) return Map.of();
        Map<String, List<String>> out = new java.util.TreeMap<>();
        try (var files = java.nio.file.Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".js") && java.nio.file.Files.isRegularFile(p)).forEach(p -> {
                try {
                    out.put(root.relativize(p).toString().replace('\\', '/'), java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8));
                } catch (java.io.IOException | java.io.UncheckedIOException e) {
                    LOGGER.debug("Material Nexus skipped unreadable script {}", p, e);
                }
            });
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            LOGGER.warn("Material Nexus could not list KubeJS scripts", e);
        }
        return out;
    }

    /** Called by the KubeJS plugin before scripts edit recipes; read after the reload. */
    public static void recipes(Supplier<List<ScriptChanges.RecipeEdit>> edits) {
        recipes = edits;
    }

    /** The recipe edits of the last load, once (nothing kept after); empty without KubeJS or if its fields changed. */
    public static List<ScriptChanges.RecipeEdit> takeRecipes() {
        Supplier<List<ScriptChanges.RecipeEdit>> edits = recipes;
        recipes = null;
        if (edits == null) return List.of();
        try {
            return edits.get();
        } catch (RuntimeException | LinkageError e) {
            // ADR-022: KubeJS's fields are public but not a stable API; tag edits still work without them.
            LOGGER.warn("Material Nexus could not read the recipe edits of KubeJS scripts; only tag edits are used", e);
            return List.of();
        }
    }
}
