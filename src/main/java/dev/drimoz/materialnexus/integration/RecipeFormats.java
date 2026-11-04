package dev.drimoz.materialnexus.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Recipe types whose JSON Material Nexus understands, and where their item outputs live (ADR-011, ADR-016).
 * Data, not code: one file per format in {@code data/<namespace>/material_nexus/recipe_formats/}, from
 * any datapack. Material Nexus ships vanilla, Create, Mekanism, IE and MI; a pack author adds another mod
 * or overrides one of ours with a file of the same id. No mod class is ever loaded, so absent mods are safe.
 *
 * <p>Everything under an output key is treated as output: item stacks ({@code "id"} or {@code "item"}),
 * lists and nested objects. Fluid and chemical outputs carry ids that are never items to convert.
 * Inputs need no description: literal {@code "item"} ingredients outside the output keys are rewritten.
 */
public final class RecipeFormats {
    public static final String DIRECTORY = "material_nexus/recipe_formats";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One format file: the recipe types it covers and the JSON keys holding their outputs. */
    public record Format(Identifier source, List<Identifier> types, List<String> outputKeys) { }

    /** {@code skip}: recipe types never rewritten, by format or by their keys (MNX-125); a file may only skip. */
    private record FormatJson(List<Identifier> types, List<String> outputs, List<Identifier> skip) {
        static final Codec<FormatJson> CODEC = RecordCodecBuilder.<FormatJson>create(i -> i.group(
                Identifier.CODEC.listOf().optionalFieldOf("types", List.of()).forGetter(FormatJson::types),
                Codec.STRING.listOf().optionalFieldOf("outputs", List.of()).forGetter(FormatJson::outputs),
                Identifier.CODEC.listOf().optionalFieldOf("skip", List.of()).forGetter(FormatJson::skip)
        ).apply(i, FormatJson::new)).validate(f -> {
            if (f.types().isEmpty() != f.outputs().isEmpty()) return DataResult.error(() -> "\"types\" and \"outputs\" go together");
            if (f.types().isEmpty() && f.skip().isEmpty()) return DataResult.error(() -> "a recipe format needs \"types\" and \"outputs\", or \"skip\"");
            return DataResult.success(f);
        });
    }

    private final Map<Identifier, Format> byType;
    private final java.util.Set<Identifier> skipped;

    private RecipeFormats(Map<Identifier, Format> byType, java.util.Set<Identifier> skipped) {
        this.byType = Map.copyOf(byType);
        this.skipped = java.util.Set.copyOf(skipped);
    }

    /** True when a format file says this type is never rewritten. */
    public boolean skips(Identifier type) {
        return skipped.contains(type);
    }

    /** Every format visible in the active datapacks; files are applied in id order, so the result is deterministic. */
    public static RecipeFormats load(ResourceManager resources) {
        Map<Identifier, JsonElement> files = new TreeMap<>();
        for (Map.Entry<Identifier, Resource> e : resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = e.getValue().openAsReader()) {
                files.put(e.getKey(), JsonParser.parseReader(reader));
            } catch (Exception ex) {
                LOGGER.warn("Ignoring unreadable recipe format {}: {}", e.getKey(), ex.getMessage());
            }
        }
        return parse(files);
    }

    /** Pure part of {@link #load}. A later file (by id) covering the same type wins; invalid files are skipped. */
    /** Why this format file cannot be read, or empty (MNX-046 in-game edits). */
    public static java.util.Optional<String> validate(JsonElement json) {
        return FormatJson.CODEC.parse(JsonOps.INSTANCE, json).error().map(e -> e.message());
    }

    public static RecipeFormats parse(Map<Identifier, JsonElement> files) {
        Map<Identifier, Format> byType = new HashMap<>();
        java.util.Set<Identifier> skipped = new java.util.HashSet<>();
        new TreeMap<>(files).forEach((file, json) -> FormatJson.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOGGER.warn("Ignoring invalid recipe format {}: {}", file, error))
                .ifPresent(f -> {
                    Format format = new Format(file, List.copyOf(f.types()), List.copyOf(f.outputs()));
                    f.types().forEach(type -> byType.put(type, format));
                    skipped.addAll(f.skip());
                }));
        return new RecipeFormats(byType, skipped);
    }

    public Optional<Format> forType(Identifier type) {
        return Optional.ofNullable(byType.get(type));
    }
}
