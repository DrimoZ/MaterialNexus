package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MNX-118: what the pack author's datapacks (neither the game, a mod jar nor Material Nexus) change in the data Material
 * Nexus reads: recipe files they replace (rewritten, or switched off) and item tags they edit. Read from the resource
 * stacks once per reload, on first need, like the recipe texts (MNX-114); never per tick. Nothing in those packs is changed.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.drimoz.materialnexus.MaterialNexus.MOD_ID)
public final class PackEdits {
    /** Preview only, never written: {@code target} an item the pack would put in or take out of tag {@code item}, against a datapack. */
    public static final String PACK_CONFLICT = "pack_conflict";

    /** One datapack's file for an item tag; {@code replace} drops what lower packs put in it. */
    public record TagEdit(String pack, boolean replace, List<ResourceLocation> added, List<ResourceLocation> removed) {
        public TagEdit {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }
    }

    /** {@code recipes}: recipe id to the datapack whose file replaces a lower one. {@code tags}: tag to its datapack edits, lowest first. */
    public record Edits(Map<ResourceLocation, String> recipes, Map<ResourceLocation, List<TagEdit>> tags) {
        public static final Edits NONE = new Edits(Map.of(), Map.of());

        public Edits {
            recipes = Map.copyOf(recipes);
            tags = Map.copyOf(tags);
        }
    }

    private record Cache(WeakReference<ResourceManager> owner, Set<String> authored, Edits edits) { }

    private static Cache cache;

    private PackEdits() { }

    /** A pack of the pack author: a world datapack, or one a datapack loader adds. Never the game, a mod jar or Material Nexus. */
    static boolean authored(Pack pack) {
        PackSource source = pack.location().source();
        if (source == PackSource.WORLD) return true;
        if (source == PackSource.BUILT_IN || source == PackSource.FEATURE || source == PackSource.DEFAULT || source == PackSource.SERVER) return false;
        return !pack.getId().startsWith("mod/") && !pack.getId().equals("mod_data");
    }

    public static Edits of(MinecraftServer server) {
        return cache(server).edits();
    }

    private static synchronized Cache cache(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        if (cache != null && cache.owner().get() == resources) return cache;
        Set<String> authored = server.getPackRepository().getSelectedPacks().stream().filter(PackEdits::authored).map(Pack::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<ResourceLocation, String> recipes = new HashMap<>();
        Map<ResourceLocation, List<TagEdit>> tags = new HashMap<>();
        if (!authored.isEmpty()) {
            resources.listResourceStacks("recipe", f -> f.getPath().endsWith(".json")).forEach((file, stack) -> {
                List<Resource> below = stack.stream().filter(r -> !r.sourcePackId().equals(GeneratedPack.PACK_ID)).toList();
                if (below.size() > 1 && authored.contains(below.getLast().sourcePackId())) recipes.put(id(file, "recipe/"), below.getLast().sourcePackId());
            });
            resources.listResourceStacks("tags/item", f -> f.getPath().endsWith(".json")).forEach((file, stack) -> {
                for (Resource r : stack) {
                    if (!authored.contains(r.sourcePackId())) continue;
                    read(r).ifPresent(json -> tags.computeIfAbsent(id(file, "tags/item/"), t -> new ArrayList<>()).add(tagEdit(r.sourcePackId(), json)));
                }
            });
        }
        cache = new Cache(new WeakReference<>(resources), authored, new Edits(recipes, tags));
        return cache;
    }

    /** Leaving a world frees what was read; the next server reads its own. */
    @net.neoforged.bus.api.SubscribeEvent
    public static synchronized void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        cache = null;
    }

    /** The recipe as the mods define it, under the datapack that replaced it: what a recipe the datapack switched off used to make. */
    public static Optional<JsonObject> beneath(MinecraftServer server, ResourceLocation recipe) {
        Set<String> authored = cache(server).authored();
        List<Resource> stack = server.getResourceManager().getResourceStack(
                ResourceLocation.fromNamespaceAndPath(recipe.getNamespace(), "recipe/" + recipe.getPath() + ".json"));
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource r = stack.get(i);
            if (!r.sourcePackId().equals(GeneratedPack.PACK_ID) && !authored.contains(r.sourcePackId())) return read(r);
        }
        return Optional.empty();
    }

    /** The datapack's own entries: item ids only ({@code #tag} references are left out), plain or as {@code {"id": ...}}. */
    public static TagEdit tagEdit(String pack, JsonObject json) {
        return new TagEdit(pack, json.has("replace") && json.get("replace").getAsBoolean(), ids(json.get("values")), ids(json.get("remove")));
    }

    /**
     * Tag changes this pack would make against a datapack: taking out of a tag an item a datapack puts in it, or putting in
     * one a datapack takes out. The generated pack is on top, so it wins in the game; the player is told.
     */
    public static List<PackContent.Effect> conflicts(List<PackContent.Effect> effects, Map<ResourceLocation, List<TagEdit>> tags) {
        List<PackContent.Effect> out = new ArrayList<>();
        for (PackContent.Effect e : effects) {
            boolean removal = e.kind().equals(PackContent.TAG_REMOVE);
            if (!removal && !e.kind().equals(PackContent.TAG_ADD)) continue;
            boolean against = tags.getOrDefault(e.target(), List.of()).stream().anyMatch(t -> (removal ? t.added() : t.removed()).contains(e.item()));
            if (against) out.add(new PackContent.Effect(PACK_CONFLICT, e.item(), e.target()));
        }
        return out;
    }

    private static List<ResourceLocation> ids(JsonElement list) {
        List<ResourceLocation> out = new ArrayList<>();
        if (!(list instanceof JsonArray array)) return out;
        for (JsonElement e : array) {
            JsonElement id = e.isJsonObject() ? e.getAsJsonObject().get("id") : e;
            if (id == null || !id.isJsonPrimitive() || id.getAsString().startsWith("#")) continue;
            ResourceLocation item = ResourceLocation.tryParse(id.getAsString());
            if (item != null) out.add(item);
        }
        return out;
    }

    /** recipe/a/b.json in namespace n to n:a/b. */
    private static ResourceLocation id(ResourceLocation file, String folder) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(folder.length(), path.length() - ".json".length()));
    }

    private static Optional<JsonObject> read(Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
