package dev.drimoz.materialnexus.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Policy presets (MNX-019) from {@code data/<namespace>/material_nexus/presets/*.json}: a partial
 * {@code global.json}. Applying one replaces the fields it defines and keeps the others, through the usual
 * Preview / Apply / Revert flow. Names come from {@code materialnexus.preset.<path>} lang keys.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class Presets extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final String DIRECTORY = "material_nexus/presets";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<Identifier, JsonObject> presets = Map.of();

    private Presets() {
        super(net.minecraft.util.ExtraCodecs.JSON, net.minecraft.resources.FileToIdConverter.json(DIRECTORY));
    }

    @SubscribeEvent
    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MaterialNexus.MOD_ID, "presets"), new Presets());
    }

    public static Map<Identifier, JsonObject> all() {
        return presets;
    }

    public static Optional<JsonObject> get(Identifier id) {
        return Optional.ofNullable(presets.get(id));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<Identifier, JsonObject> loaded = new TreeMap<>();
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
