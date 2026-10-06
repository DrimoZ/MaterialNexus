package dev.drimoz.materialnexus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
import dev.drimoz.materialnexus.datapack.RecipeRewrites.Source;
import dev.drimoz.materialnexus.integration.RecipeFormats;
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

    /** The formats Material Nexus ships, read from the same files the game loads. */
    private static final RecipeFormats SHIPPED = shipped();

    private static RecipeFormats shipped() {
        Map<ResourceLocation, com.google.gson.JsonElement> files = new java.util.HashMap<>();
        for (String name : List.of("vanilla", "create", "mekanism", "immersiveengineering", "modern_industrialization")) {
            try (var in = RecipeRewritesTest.class.getResourceAsStream("/data/materialnexus/material_nexus/recipe_formats/" + name + ".json")) {
                assertNotNull(in, name);
                files.put(ResourceLocation.fromNamespaceAndPath("materialnexus", name),
                        JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return RecipeFormats.parse(files);
    }

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

        PackContent.Content plan = RecipeRewrites.plan(sources, Map.of(IE, MEK), SHIPPED);

        assertEquals(List.of(
                new PackContent.Effect(RecipeRewrites.UNSUPPORTED, id("ie_crusher"), IE),
                new PackContent.Effect(RecipeRewrites.DISABLE, id("ie_from_block"), MEK),
                new PackContent.Effect(RecipeRewrites.REWRITE, id("ie_smelt"), MEK)), plan.effects());
        assertTrue(plan.files().get("data/test/recipe/ie_smelt.json").toString().contains("\"id\":\"mekanism:ingot_tin\""));
        assertTrue(plan.files().get("data/test/recipe/ie_from_block.json").toString().contains("neoforge:false"));
        assertFalse(plan.files().containsKey("data/test/recipe/mek_from_block.json"), "an untouched recipe is never overridden");
        assertFalse(plan.files().containsKey("data/test/recipe/ie_crusher.json"), "unsupported recipes are never overridden");
    }

    /** Reported in game: IE "ingot to nuggets" became IE ingot -> MI nuggets. Inputs must follow the unification too. */
    @Test
    void alternativeInputsAreRewrittenSoTheRecipeCanCollapseIntoItsDuplicate() {
        ResourceLocation ieIngot = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "ingot_aluminum");
        ResourceLocation ieNugget = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "nugget_aluminum");
        ResourceLocation miIngot = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "aluminum_ingot");
        ResourceLocation miNugget = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "aluminum_nugget");
        String toNuggets = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"%s\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
        String fromTag = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"c:ingots/aluminum\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
        var sources = List.of(
                new Source(id("ie_to_nuggets"), json(toNuggets.formatted(ieIngot, ieNugget)), ieNugget),
                new Source(id("mi_to_nuggets"), json(toNuggets.formatted(miIngot, miNugget)), miNugget),
                // Only consumes an alternative through a tag: nothing literal to rewrite, left untouched.
                new Source(id("tag_to_nuggets"), json(fromTag.formatted(miNugget)), miNugget));

        PackContent.Content plan = RecipeRewrites.plan(sources, Map.of(ieIngot, miIngot, ieNugget, miNugget), SHIPPED);

        assertEquals(List.of(new PackContent.Effect(RecipeRewrites.DISABLE, id("ie_to_nuggets"), miNugget)), plan.effects());
        assertEquals(1, plan.files().size());
    }

    /** MNX-015/016: Create and Mekanism formats as shipped (Create 6.0.10, Mekanism 10.7.19). */
    @Test
    void createAndMekanismOutputsAndInputsAreRewritten() {
        ResourceLocation createSheet = ResourceLocation.fromNamespaceAndPath("create", "copper_sheet");
        ResourceLocation iePlate = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "plate_copper");
        ResourceLocation mekDust = ResourceLocation.fromNamespaceAndPath("mekanism", "dust_copper");
        ResourceLocation miDust = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_dust");
        var conversions = Map.of(createSheet, iePlate, mekDust, miDust);
        var sources = List.of(
                new Source(id("create_pressing"), json("{\"type\":\"create:pressing\",\"ingredients\":[{\"tag\":\"c:ingots/copper\"}],"
                        + "\"results\":[{\"id\":\"create:copper_sheet\"},{\"chance\":0.5,\"id\":\"minecraft:copper_nugget\"}]}"), createSheet),
                new Source(id("mek_enriching"), json("{\"type\":\"mekanism:enriching\",\"input\":{\"count\":1,\"item\":\"mekanism:dust_copper\"},"
                        + "\"output\":{\"count\":1,\"id\":\"minecraft:copper_ingot\"}}"), ResourceLocation.withDefaultNamespace("copper_ingot")),
                new Source(id("mek_sawing"), json("{\"type\":\"mekanism:sawing\",\"input\":{\"count\":1,\"tag\":\"c:ores/copper\"},"
                        + "\"main_output\":{\"count\":2,\"id\":\"mekanism:dust_copper\"},\"secondary_output\":{\"chance\":0.1,\"id\":\"create:copper_sheet\"}}"), mekDust),
                // Chemical output: its id is never an item to convert, so the recipe is untouched.
                new Source(id("mek_dissolution"), json("{\"type\":\"mekanism:dissolution\",\"output\":{\"amount\":1000,\"id\":\"mekanism:copper\"}}"),
                        id("mek_dissolution")));

        PackContent.Content plan = RecipeRewrites.plan(sources, conversions, SHIPPED);
        String pressing = plan.files().get("data/test/recipe/create_pressing.json").toString();
        String enriching = plan.files().get("data/test/recipe/mek_enriching.json").toString();
        String sawing = plan.files().get("data/test/recipe/mek_sawing.json").toString();

        assertTrue(pressing.contains("\"id\":\"immersiveengineering:plate_copper\"") && pressing.contains("\"chance\":0.5"), pressing);
        assertTrue(enriching.contains("\"item\":\"modern_industrialization:copper_dust\"") && enriching.contains("\"count\":1"), enriching);
        assertTrue(sawing.contains("\"id\":\"modern_industrialization:copper_dust\"") && sawing.contains("\"id\":\"immersiveengineering:plate_copper\""), sawing);
        assertFalse(plan.files().containsKey("data/test/recipe/mek_dissolution.json"));
    }

    /** MNX-017/018: IE nested secondaries and MI item_outputs, plus a pack-author format overriding a shipped one. */
    @Test
    void ieAndMiFormatsAndAuthorOverrides() {
        ResourceLocation mekDust = ResourceLocation.fromNamespaceAndPath("mekanism", "dust_copper");
        ResourceLocation miDust = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_dust");
        var conversions = Map.of(mekDust, miDust);
        var sources = List.of(
                new Source(id("ie_crusher"), json("{\"type\":\"immersiveengineering:crusher\",\"input\":{\"tag\":\"c:ores/copper\"},"
                        + "\"result\":{\"id\":\"minecraft:raw_copper\"},\"secondaries\":[{\"chance\":0.1,\"output\":{\"id\":\"mekanism:dust_copper\"}}]}"),
                        ResourceLocation.withDefaultNamespace("raw_copper")),
                new Source(id("mi_macerator"), json("{\"type\":\"modern_industrialization:macerator\",\"item_inputs\":[{\"amount\":1,\"item\":\"mekanism:dust_copper\"}],"
                        + "\"item_outputs\":[{\"amount\":2,\"item\":\"mekanism:dust_copper\"}]}"), mekDust));

        PackContent.Content plan = RecipeRewrites.plan(sources, conversions, SHIPPED);
        assertTrue(plan.files().get("data/test/recipe/ie_crusher.json").toString().contains("\"id\":\"modern_industrialization:copper_dust\""));
        assertFalse(plan.files().get("data/test/recipe/mi_macerator.json").toString().contains("mekanism:dust_copper"));

        // A pack author describes another mod's recipe type in their own datapack; it is then rewritten too.
        var withAuthor = new java.util.HashMap<ResourceLocation, com.google.gson.JsonElement>();
        withAuthor.put(ResourceLocation.fromNamespaceAndPath("mypack", "othermod"),
                JsonParser.parseString("{\"types\":[\"othermod:grinder\"],\"outputs\":[\"product\"]}"));
        var custom = new Source(id("other_grinder"), json("{\"type\":\"othermod:grinder\",\"product\":{\"id\":\"mekanism:dust_copper\"}}"), mekDust);
        assertEquals(List.of(new PackContent.Effect(RecipeRewrites.UNSUPPORTED, id("other_grinder"), mekDust)),
                RecipeRewrites.plan(List.of(custom), conversions, SHIPPED).effects());
        assertEquals(List.of(new PackContent.Effect(RecipeRewrites.REWRITE, id("other_grinder"), miDust)),
                RecipeRewrites.plan(List.of(custom), conversions, RecipeFormats.parse(withAuthor)).effects());
    }
}
