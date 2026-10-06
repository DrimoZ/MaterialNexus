package dev.drimoz.materialnexus.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.domain.MaterialDefinition;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Explicit material definitions (MNX-033) from {@code data/<namespace>/material_nexus/materials/*.json}:
 * {@code {"id": "aluminum", "aliases": ["aluminium"]}}. Loaded with the other data, before tags are
 * bound, so discovery sees them. Aliases merge a material named differently by some mods into this one.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class MaterialDefinitions extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "material_nexus/materials";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, MaterialId> aliases = Map.of();

    private MaterialDefinitions() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new MaterialDefinitions());
    }

    /** Alias name to the material that declares it. */
    public static Map<String, MaterialId> aliases() {
        return aliases;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        aliases = Map.copyOf(parse(files));
    }

    /** Pure part: files in id order; an alias claimed twice keeps the first claim and is reported. */
    public static Map<String, MaterialId> parse(Map<ResourceLocation, JsonElement> files) {
        Map<String, MaterialId> result = new HashMap<>();
        new TreeMap<>(files).forEach((file, json) -> MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.warn("Ignoring invalid material definition {}: {}", file, error))
                .ifPresent(def -> def.aliases().forEach(alias -> {
                    if (alias.equals(def.id().name())) return;
                    MaterialId previous = result.putIfAbsent(alias, def.id());
                    if (previous != null && !previous.equals(def.id())) {
                        LOGGER.warn("Alias '{}' is claimed by both {} and {} ({}); keeping {}", alias, previous, def.id(), file, previous);
                    }
                })));
        return result;
    }
}
