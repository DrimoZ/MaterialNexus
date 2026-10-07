package dev.drimoz.materialnexus.datapack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The generated datapack (ADR-007): build output in {@code config/materialnexus/generated/},
 * overwritten as a whole, injected into every world at the top of the pack list.
 */
public final class GeneratedPack {
    public static final String PACK_ID = "materialnexus_generated";
    /** Tags of the items created for missing forms (MNX-039), rebuilt from the registered items at every pack scan. */
    public static final String ITEMS_PACK_ID = "materialnexus_items";
    public static final String MARKER = "_GENERATED_DO_NOT_EDIT";
    public static final String MANIFEST = "manifest.json";
    private static final Logger LOGGER = LogUtils.getLogger();

    private GeneratedPack() { }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        Path items = MnxPaths.root().resolve("created_items");
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            // MNX-039: the created items' models live in the same pack, so the client loads it as a resource pack too.
            if (prepareItems(items)) add(event, ITEMS_PACK_ID, items, PackType.CLIENT_RESOURCES);
            return;
        }
        if (event.getPackType() != PackType.SERVER_DATA) return;
        Path dir = MnxPaths.generated();
        try {
            if (!isOurs(dir)) {
                LOGGER.warn("{} exists but was not created by Material Nexus; it is left untouched and not loaded", dir);
            } else {
                if (!Files.exists(dir.resolve(MARKER))) write(dir, Map.of(), new JsonArray());
                add(event, PACK_ID, dir);
            }
        } catch (IOException e) {
            LOGGER.error("Could not prepare the Material Nexus generated pack", e);
        }
        if (prepareItems(items)) add(event, ITEMS_PACK_ID, items);
        // MNX-046: in-game edits of Material Nexus data, a plain user-owned datapack under policies/.
        try {
            EditableData.ensurePack(EditableData.userPack());
            add(event, EditableData.USER_PACK_ID, EditableData.userPack());
        } catch (IOException e) {
            LOGGER.error("Could not prepare the Material Nexus edits pack", e);
        }
    }

    /**
     * One convention tag file per created item's tag (the items are registered, so plain values), and one item model
     * per item, parented to its form's template: the game finds a model for every item and logs no warning.
     */
    private static Map<String, JsonElement> createdItemFiles() {
        Map<String, JsonObject> files = new java.util.TreeMap<>();
        for (var item : dev.drimoz.materialnexus.registry.MnxItems.CREATED) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", "materialnexus:item/template_" + item.get().entry().form().name());
            files.put("assets/" + item.getId().getNamespace() + "/models/item/" + item.getId().getPath() + ".json", model);
        }
        for (var item : dev.drimoz.materialnexus.registry.MnxItems.CREATED) {
            var tag = item.get().entry().tag();
            files.computeIfAbsent("data/" + tag.getNamespace() + "/tags/item/" + tag.getPath() + ".json", p -> {
                JsonObject file = new JsonObject();
                file.addProperty("replace", false);
                file.add("values", new JsonArray());
                return file;
            }).getAsJsonArray("values").add(item.getId().toString());
        }
        return Map.copyOf(files);
    }

    /**
     * The created items pack, rewritten only when its content changed: rewriting replaces the folder, which Windows
     * refuses while anything holds a file in it (seen as AccessDeniedException on the rename). If writing fails, the
     * pack already on disk is still used. True when there is a pack to load.
     */
    private static boolean prepareItems(Path items) {
        try {
            if (!isOurs(items)) {
                LOGGER.warn("{} exists but was not created by Material Nexus; it is left untouched and not loaded", items);
                return false;
            }
            Map<String, JsonElement> files = createdItemFiles();
            if (!sameContent(items, files)) write(items, files, new JsonArray());
        } catch (IOException e) {
            LOGGER.error("Could not update the Material Nexus created items pack; using the one on disk", e);
        }
        return Files.exists(items.resolve(MARKER));
    }

    /** True when {@code dir} holds exactly these data/assets files with this content. */
    private static boolean sameContent(Path dir, Map<String, JsonElement> files) throws IOException {
        if (!Files.exists(dir.resolve(MARKER))) return false;
        var gson = new GsonBuilder().setPrettyPrinting().create();
        for (var e : files.entrySet()) {
            Path file = dir.resolve(e.getKey());
            if (!Files.isRegularFile(file) || !Files.readString(file, StandardCharsets.UTF_8).equals(gson.toJson(e.getValue()))) return false;
        }
        long onDisk = 0;
        for (String root : new String[] {"data", "assets"}) {
            Path r = dir.resolve(root);
            if (!Files.isDirectory(r)) continue;
            try (Stream<Path> walk = Files.walk(r)) {
                onDisk += walk.filter(Files::isRegularFile).count();
            }
        }
        return onDisk == files.size();
    }

    private static void add(AddPackFindersEvent event, String id, Path dir) {
        add(event, id, dir, PackType.SERVER_DATA);
    }

    private static void add(AddPackFindersEvent event, String id, Path dir, PackType type) {
        var info = new PackLocationInfo(id, Component.translatable("materialnexus.pack.generated"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir), type,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /**
     * Replaces the whole pack with {@code files} (pack-relative path to JSON) and a manifest of
     * {@code changes}. Refuses to delete a folder Material Nexus did not create.
     */
    public static void write(Path dir, Map<String, JsonElement> files, JsonArray changes) throws IOException {
        if (!isOurs(dir)) throw new IOException("Refusing to overwrite " + dir + ": it has no " + MARKER + " marker");
        Path tmp = dir.resolveSibling(dir.getFileName() + ".tmp");
        deleteRecursively(tmp);
        Files.createDirectories(tmp);

        Files.writeString(tmp.resolve(MARKER), "Generated by Material Nexus from config/materialnexus/policies/. Edits here are overwritten.\n");
        writeJson(tmp.resolve("pack.mcmeta"), packMeta());
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", 1);
        manifest.add("changes", changes);
        writeJson(tmp.resolve(MANIFEST), manifest);
        for (var entry : files.entrySet()) {
            Path target = tmp.resolve(entry.getKey()).normalize();
            if (!target.startsWith(tmp)) throw new IOException("Generated path escapes the pack: " + entry.getKey());
            Files.createDirectories(target.getParent());
            writeJson(target, entry.getValue());
        }

        // ponytail: delete-then-move is not atomic; a crash in between leaves no pack, and the next
        // startup writes an empty one. Upgrade to a rename-swap if that window ever matters.
        deleteRecursively(dir);
        Files.move(tmp, dir);
    }

    /** True if MNX may manage this folder: absent, empty, or carrying our marker. */
    static boolean isOurs(Path dir) throws IOException {
        if (!Files.exists(dir) || Files.exists(dir.resolve(MARKER))) return true;
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    private static JsonObject packMeta() {
        JsonObject description = new JsonObject();
        description.addProperty("translate", "materialnexus.pack.generated");
        JsonObject pack = new JsonObject();
        pack.add("description", description);
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        // The created items pack is also a resource pack, whose format number differs.
        JsonArray supported = new JsonArray();
        supported.add(0);
        supported.add(1000);
        pack.add("supported_formats", supported);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static void writeJson(Path file, JsonElement json) throws IOException {
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
