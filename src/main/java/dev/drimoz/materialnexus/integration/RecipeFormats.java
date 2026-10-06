package dev.drimoz.materialnexus.integration;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recipe types whose JSON Material Nexus understands, and where their item outputs live (ADR-011).
 * Pure data: no class of Create or Mekanism is ever loaded, so this is safe when they are absent.
 * Outputs are item stacks ({@code {"id": ...}}) or lists of them; fluid and chemical outputs carry ids
 * that are never items to convert, so they are left alone. Inputs are always rewritten the same way:
 * literal {@code "item"} ingredients outside the output keys.
 */
public final class RecipeFormats {
    /** Where one family of recipe types keeps its outputs. */
    public record Format(String adapter, List<String> outputKeys) { }

    private static final Map<ResourceLocation, Format> BY_TYPE = new HashMap<>();

    static {
        register("minecraft", new Format("vanilla", List.of("result")),
                "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
                "campfire_cooking", "stonecutting", "smithing_transform");
        // Create 6: processing recipes list weighted results; sequenced assembly nests them and stays unsupported.
        register("create", new Format("create", List.of("results")),
                "crushing", "milling", "pressing", "mixing", "compacting", "splashing", "haunting",
                "deploying", "filling", "emptying", "cutting", "item_application", "sandpaper_polishing");
        register("create", new Format("create", List.of("result")), "mechanical_crafting");
        // Mekanism 10.7: one or two item outputs under these keys; chemical outputs share "output" but are not items.
        register("mekanism", new Format("mekanism", List.of("output", "main_output", "secondary_output", "item_output")),
                "crushing", "enriching", "sawing", "injecting", "combining", "metallurgic_infusing", "purifying",
                "nucleosynthesizing", "crystallizing", "compressing", "reaction");
    }

    private RecipeFormats() { }

    private static void register(String namespace, Format format, String... types) {
        for (String type : types) BY_TYPE.put(ResourceLocation.fromNamespaceAndPath(namespace, type), format);
    }

    public static Optional<Format> forType(ResourceLocation type) {
        return Optional.ofNullable(BY_TYPE.get(type));
    }
}
