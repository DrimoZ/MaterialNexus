package dev.drimoz.materialnexus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
import dev.drimoz.materialnexus.datapack.RecipeRewrites.Source;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Recipes are the balance of the pack: rewrite only what we understand, never drop an untouched recipe. */
class RecipeRewritesTest {
    private static final ResourceLocation MEK = ResourceLocation.fromNamespaceAndPath("mekanism", "ingot_tin");
    private static final ResourceLocation IE = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "ingot_tin");

    private static Optional<JsonObject> json(String text) {
        return Optional.of(JsonParser.parseString(text).getAsJsonObject());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void rewritesVanillaOutputsAndDisablesOnlyNewDuplicates() {
        String block = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"c:storage_blocks/tin\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
        var sources = List.of(
                // Untouched Mekanism recipe already producing the canonical ingot.
                new Source(id("mek_from_block"), json(block.formatted(MEK)), MEK),
                // IE recipe identical except for its output: becomes an exact duplicate, so it is disabled.
                new Source(id("ie_from_block"), json(block.formatted(IE)), IE),
                // IE smelting: rewritten in place.
                new Source(id("ie_smelt"), json("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"tag\":\"c:raw_materials/tin\"},\"result\":{\"id\":\"" + IE + "\"},\"experience\":0.7,\"cookingtime\":200}"), IE),
                // Modded type: no JSON we understand, listed and left alone.
                new Source(id("ie_crusher"), Optional.empty(), IE));

        PackContent.Content plan = RecipeRewrites.plan(sources, Map.of(IE, MEK));

        assertEquals(List.of(
                new PackContent.Effect(RecipeRewrites.UNSUPPORTED, id("ie_crusher"), IE),
                new PackContent.Effect(RecipeRewrites.DISABLE, id("ie_from_block"), MEK),
                new PackContent.Effect(RecipeRewrites.REWRITE, id("ie_smelt"), MEK)), plan.effects());
        assertTrue(plan.files().get("data/test/recipe/ie_smelt.json").toString().contains("\"id\":\"mekanism:ingot_tin\""));
        assertTrue(plan.files().get("data/test/recipe/ie_from_block.json").toString().contains("neoforge:false"));
        assertFalse(plan.files().containsKey("data/test/recipe/mek_from_block.json"), "an untouched recipe is never overridden");
        assertFalse(plan.files().containsKey("data/test/recipe/ie_crusher.json"), "unsupported recipes are never overridden");
    }
}
