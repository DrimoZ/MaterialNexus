package dev.drimoz.materialnexus.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Policy presets (MNX-019) from {@code data/<namespace>/material_nexus/presets/*.json}: a partial
 * {@code global.json}. Applying one replaces the fields it defines and keeps the others, through the usual
 * Preview / Apply / Revert flow. Names come from {@code materialnexus.preset.<path>} lang keys.
 */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class Presets extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "material_nexus/presets";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, JsonObject> presets = Map.of();

    private Presets() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new Presets());
    }

    public static Map<ResourceLocation, JsonObject> all() {
        return presets;
    }

    public static Optional<JsonObject> get(ResourceLocation id) {
        return Optional.ofNullable(presets.get(id));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> loaded = new TreeMap<>();
        files.forEach((id, json) -> {
            if (json.isJsonObject()) loaded.put(id, json.getAsJsonObject());
            else LOGGER.warn("Ignoring preset {}: not a JSON object", id);
        });
        presets = java.util.Collections.unmodifiableMap(loaded);
    }

    /** The global policy with the preset applied: its fields replace the same fields, the others are kept. */
    public static JsonObject overlay(JsonObject global, JsonObject preset) {
        JsonObject merged = global.deepCopy();
        preset.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
        return merged;
    }
}
