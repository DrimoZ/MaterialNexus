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
    static final RecipeFormats SHIPPED = shipped();

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

    /**
     * MNX-127: formats are shipped for many mods and keyed by recipe type, so they only act where the mod is there. What has to hold
     * for all of them: each file reads, and no type is claimed by two files (the later one would silently replace the earlier).
     */
    @Test
    void everyShippedFormatReadsAndNoTypeIsClaimedTwice() throws Exception {
        Map<ResourceLocation, String> owner = new java.util.HashMap<>();
        var directory = java.nio.file.Path.of(RecipeRewritesTest.class.getResource("/data/materialnexus/material_nexus/recipe_formats").toURI());
        try (var files = java.nio.file.Files.list(directory)) {
            for (java.nio.file.Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                var json = JsonParser.parseString(java.nio.file.Files.readString(file));
                assertTrue(RecipeFormats.validate(json).isEmpty(), file + ": " + RecipeFormats.validate(json));
                if (!json.getAsJsonObject().has("types")) continue;
                for (var type : json.getAsJsonObject().getAsJsonArray("types")) {
                    String previous = owner.put(ResourceLocation.parse(type.getAsString()), file.getFileName().toString());
                    assertNull(previous, type.getAsString() + " is in " + previous + " and in " + file.getFileName());
                }
            }
        }
        assertTrue(owner.size() > 100, "the shipped formats cover " + owner.size() + " recipe types");
    }

    /** MNX-109: a written recipe is skipped, not fatal, once a mod it names is gone (1.20.1 cooking recipes throw). */
    @Test
    void writtenRecipesLoadOnlyWhileTheirItemsExist() {
        final String DISABLED = "{\"conditions\":[{\"type\":\"forge:false\"}]}";
        String smelting = "{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"mekanism:dust_bronze\"},\"result\":\"mekanism:ingot_bronze\","
                + "\"conditions\":[{\"type\":\"forge:mod_loaded\",\"modid\":\"thermal\"}]}";
        var content = new PackContent.Content(Map.of(
                "data/thermal/recipes/bronze_from_dust.json", JsonParser.parseString(smelting),
                "data/test/recipes/vanilla_only.json", JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"minecraft:iron_ingot\"}"),
                "data/test/recipes/disabled.json", JsonParser.parseString(DISABLED)), List.of());
        var files = PackContent.withItemConditions(content, SHIPPED).files();
        assertEquals(JsonParser.parseString("[{\"type\":\"forge:mod_loaded\",\"modid\":\"thermal\"},"
                        + "{\"type\":\"forge:item_exists\",\"item\":\"mekanism:dust_bronze\"},{\"type\":\"forge:item_exists\",\"item\":\"mekanism:ingot_bronze\"}]"),
                files.get("data/thermal/recipes/bronze_from_dust.json").getAsJsonObject().get("conditions"));
        assertFalse(files.get("data/test/recipes/vanilla_only.json").getAsJsonObject().has("conditions"), "vanilla items are always there");
        assertEquals(JsonParser.parseString(DISABLED), files.get("data/test/recipes/disabled.json"));
    }

    private static Optional<JsonObject> json(String text) {
        return Optional.of(JsonParser.parseString(text).getAsJsonObject());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    /**
     * MNX-125, read off 292 mods: the real recipe of a conditional wrapper is rewritten, a shaped grid's {@code key} counts as
     * input, and a type a format file {@code skip}s is never touched.
     */
    @Test
    void wrappersGridKeysAndSkippedTypes() {
        var all = dev.drimoz.materialnexus.core.policy.AlmostUnified.Ownership.ALL;
        var alt = ResourceLocation.parse("mekanism:dust_tin");
        var kept = ResourceLocation.parse("immersiveengineering:dust_tin");
        Map<ResourceLocation, ResourceLocation> conversions = Map.of(alt, kept);
        String wrapped = "{\"type\":\"forge:conditional\",\"recipes\":[{\"conditions\":[{\"type\":\"forge:mod_loaded\",\"modid\":\"othermod\"}],"
                + "\"recipe\":{\"type\":\"othermod:grinder\",\"ingredient\":{\"item\":\"%1$s\"},\"result\":{\"item\":\"%1$s\"}}}]}";
        String grid = "{\"type\":\"othermod:shaped_table\",\"pattern\":[\"#\"],\"key\":{\"#\":{\"item\":\"%1$s\"}},\"result\":{\"item\":\"othermod:thing\"}}";
        var sources = List.of(new Source(id("wrapped"), json(wrapped.formatted(alt)), alt), new Source(id("grid"), json(grid.formatted(alt)), alt));

        var plan = RecipeRewrites.plan(sources, conversions, SHIPPED, all, Map.of(), RecipeRewrites.Decoder.NONE);
        assertEquals(JsonParser.parseString("{\"item\":\"" + kept + "\"}"), plan.files().get("data/test/recipes/wrapped.json").getAsJsonObject()
                .getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonObject("recipe").get("result"));
        assertEquals(JsonParser.parseString("{\"#\":{\"item\":\"" + kept + "\"}}"), plan.files().get("data/test/recipes/grid.json").getAsJsonObject().get("key"));

        // MNX-126, found loading 33 more mods: a tag spelled like the item, a load condition and a group are not items left behind.
        String labels = "{\"type\":\"othermod:shaped_table\",\"group\":\"%1$s\",\"conditions\":[{\"type\":\"forge:item_exists\",\"item\":\"%1$s\"}],"
                + "\"key\":{\"/\":{\"tag\":\"%1$s\"}},\"pattern\":[\"/\"],\"result\":{\"item\":\"othermod:thing\"}}";
        assertTrue(RecipeRewrites.plan(List.of(new Source(id("labels"), json(labels.formatted(alt)), alt)), conversions, SHIPPED, all, Map.of(),
                RecipeRewrites.Decoder.NONE).effects().stream().noneMatch(e -> e.kind().equals(RecipeRewrites.UNSUPPORTED)));

        var skip = RecipeFormats.parse(Map.of(id("skip"), JsonParser.parseString("{\"skip\":[\"othermod:grinder\"]}")));
        var skipped = RecipeRewrites.plan(sources.subList(0, 1), conversions, skip, all, Map.of(), RecipeRewrites.Decoder.NONE);
        assertTrue(skipped.files().isEmpty(), "a skipped type is never rewritten");
        assertTrue(skipped.effects().stream().anyMatch(e -> e.kind().equals(RecipeRewrites.UNSUPPORTED)), "and it is listed");
    }

    /**
     * ADR-025, the field report: a foundry with no format still gets the kept plate out and the plate tag in, an unknown key
     * is left and listed, a tag the game would refuse falls back to the item, and a second pass changes nothing.
     */
    @Test
    void recipesWithoutAFormatAreRewrittenByTheirKeys() {
        var igPlate = ResourceLocation.parse("immersivegeology:plate_chromium");
        var kept = ResourceLocation.parse("modern_industrialization:chromium_plate");
        var tag = ResourceLocation.parse("c:plates/chromium");
        var all = dev.drimoz.materialnexus.core.policy.AlmostUnified.Ownership.ALL;
        Map<ResourceLocation, ResourceLocation> conversions = Map.of(igPlate, kept);
        String foundry = "{\"type\":\"immersivegeology:foundry\",\"inputs\":[{\"item\":\"%1$s\"},{\"tag\":\"c:dusts/coal\"}],"
                + "\"result\":{\"id\":\"%1$s\",\"count\":2},\"block\":{\"item\":\"%1$s\"}}";
        var source = new Source(id("foundry"), json(foundry.formatted(igPlate)), igPlate);
        // A machine that hides its recipe from the game (GregTech-like): it decodes, nothing more is known.
        RecipeRewrites.Decoder hidden = json -> Optional.of(new RecipeRewrites.Decoded() {
            public boolean exposed() { return false; }
            public boolean produces(ResourceLocation item) { return false; }
            public boolean consumes(ResourceLocation item) { return false; }
        });
        var form = new RecipeRewrites.UnifiedForm(tag, kept, List.of(igPlate), java.util.Set.of(igPlate, kept));
        var inputTags = RecipeRewrites.inputTags(List.of(form), Map.of(tag, java.util.Set.of(igPlate, kept)));

        var plan = RecipeRewrites.plan(List.of(source), conversions, SHIPPED, all, inputTags, hidden);
        JsonObject written = plan.files().get("data/test/recipes/foundry.json").getAsJsonObject();
        assertEquals(JsonParser.parseString("[{\"tag\":\"c:plates/chromium\"},{\"tag\":\"c:dusts/coal\"}]"), written.get("inputs"));
        assertEquals(JsonParser.parseString("{\"id\":\"" + kept + "\",\"count\":2}"), written.get("result"));
        assertEquals(JsonParser.parseString("{\"item\":\"" + igPlate + "\"}"), written.get("block"), "an unknown key is not rewritten");
        assertTrue(plan.effects().contains(new PackContent.Effect(RecipeRewrites.BY_KEYS, id("foundry"), ResourceLocation.parse("immersivegeology:foundry"))));
        assertTrue(plan.effects().contains(new PackContent.Effect(RecipeRewrites.UNSUPPORTED, id("foundry"), igPlate)));

        assertTrue(RecipeRewrites.plan(List.of(new Source(id("foundry"), Optional.of(written), kept)), conversions, SHIPPED, all, inputTags, hidden)
                .files().isEmpty(), "a second pass changes nothing");
        assertEquals(JsonParser.parseString("{\"item\":\"" + kept + "\"}"),
                RecipeRewrites.plan(List.of(source), conversions, SHIPPED, all, inputTags, RecipeRewrites.Decoder.NONE)
                        .files().get("data/test/recipes/foundry.json").getAsJsonObject().getAsJsonArray("inputs").get(0), "no tag the game refuses");
        assertTrue(RecipeRewrites.inputTags(List.of(form), Map.of(tag, java.util.Set.of(igPlate, kept, ResourceLocation.parse("othermod:plate_iron"))))
                .isEmpty(), "a tag holding another material keeps the item");
    }

    @Test
    void rewritesVanillaOutputsAndDisablesOnlyNewDuplicates() {
        String block = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"forge:storage_blocks/tin\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
        var sources = List.of(
                // Untouched Mekanism recipe already producing the canonical ingot.
                new Source(id("mek_from_block"), json(block.formatted(MEK)), MEK),
                // IE recipe identical except for its output: becomes an exact duplicate, so it is disabled.
                new Source(id("ie_from_block"), json(block.formatted(IE)), IE),
                // IE smelting: rewritten in place.
                new Source(id("ie_smelt"), json("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"tag\":\"forge:raw_materials/tin\"},\"result\":{\"id\":\"" + IE + "\"},\"experience\":0.7,\"cookingtime\":200}"), IE),
                // Modded type: no JSON we understand, listed and left alone.
                new Source(id("ie_crusher"), Optional.empty(), IE));

        PackContent.Content plan = RecipeRewrites.plan(sources, Map.of(IE, MEK), SHIPPED);

        assertEquals(List.of(
                new PackContent.Effect(RecipeRewrites.UNSUPPORTED, id("ie_crusher"), IE),
                new PackContent.Effect(RecipeRewrites.DISABLE, id("ie_from_block"), MEK),
                new PackContent.Effect(RecipeRewrites.REWRITE, id("ie_smelt"), MEK)), plan.effects());
        assertTrue(plan.files().get("data/test/recipes/ie_smelt.json").toString().contains("\"id\":\"mekanism:ingot_tin\""));
        assertTrue(plan.files().get("data/test/recipes/ie_from_block.json").toString().contains("forge:false"));
        assertFalse(plan.files().containsKey("data/test/recipes/mek_from_block.json"), "an untouched recipe is never overridden");
        assertFalse(plan.files().containsKey("data/test/recipes/ie_crusher.json"), "unsupported recipes are never overridden");
    }

    /** Reported in game: IE "ingot to nuggets" became IE ingot -> MI nuggets. Inputs must follow the unification too. */
    @Test
    void alternativeInputsAreRewrittenSoTheRecipeCanCollapseIntoItsDuplicate() {
        ResourceLocation ieIngot = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "ingot_aluminum");
        ResourceLocation ieNugget = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "nugget_aluminum");
        ResourceLocation miIngot = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "aluminum_ingot");
        ResourceLocation miNugget = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "aluminum_nugget");
        String toNuggets = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"%s\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
        String fromTag = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"forge:ingots/aluminum\"}],\"result\":{\"count\":9,\"id\":\"%s\"}}";
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
                new Source(id("create_pressing"), json("{\"type\":\"create:pressing\",\"ingredients\":[{\"tag\":\"forge:ingots/copper\"}],"
                        + "\"results\":[{\"id\":\"create:copper_sheet\"},{\"chance\":0.5,\"id\":\"minecraft:copper_nugget\"}]}"), createSheet),
                new Source(id("mek_enriching"), json("{\"type\":\"mekanism:enriching\",\"input\":{\"count\":1,\"item\":\"mekanism:dust_copper\"},"
                        + "\"output\":{\"count\":1,\"id\":\"minecraft:copper_ingot\"}}"), ResourceLocation.withDefaultNamespace("copper_ingot")),
                new Source(id("mek_sawing"), json("{\"type\":\"mekanism:sawing\",\"input\":{\"count\":1,\"tag\":\"forge:ores/copper\"},"
                        + "\"mainOutput\":{\"count\":2,\"id\":\"mekanism:dust_copper\"},\"secondaryOutput\":{\"chance\":0.1,\"id\":\"create:copper_sheet\"}}"), mekDust),
                // Chemical output: its id is never an item to convert, so the recipe is untouched.
                new Source(id("mek_dissolution"), json("{\"type\":\"mekanism:dissolution\",\"output\":{\"amount\":1000,\"id\":\"mekanism:copper\"}}"),
                        id("mek_dissolution")));

        PackContent.Content plan = RecipeRewrites.plan(sources, conversions, SHIPPED);
        String pressing = plan.files().get("data/test/recipes/create_pressing.json").toString();
        String enriching = plan.files().get("data/test/recipes/mek_enriching.json").toString();
        String sawing = plan.files().get("data/test/recipes/mek_sawing.json").toString();

        assertTrue(pressing.contains("\"id\":\"immersiveengineering:plate_copper\"") && pressing.contains("\"chance\":0.5"), pressing);
        assertTrue(enriching.contains("\"item\":\"modern_industrialization:copper_dust\"") && enriching.contains("\"count\":1"), enriching);
        assertTrue(sawing.contains("\"id\":\"modern_industrialization:copper_dust\"") && sawing.contains("\"id\":\"immersiveengineering:plate_copper\""), sawing);
        assertFalse(plan.files().containsKey("data/test/recipes/mek_dissolution.json"));
    }

    /** MNX-017/018: IE nested secondaries and MI item_outputs, plus a pack-author format overriding a shipped one. */
    @Test
    void ieAndMiFormatsAndAuthorOverrides() {
        ResourceLocation mekDust = ResourceLocation.fromNamespaceAndPath("mekanism", "dust_copper");
        ResourceLocation miDust = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_dust");
        var conversions = Map.of(mekDust, miDust);
        var sources = List.of(
                new Source(id("ie_crusher"), json("{\"type\":\"immersiveengineering:crusher\",\"input\":{\"tag\":\"forge:ores/copper\"},"
                        + "\"result\":{\"id\":\"minecraft:raw_copper\"},\"secondaries\":[{\"chance\":0.1,\"output\":{\"id\":\"mekanism:dust_copper\"}}]}"),
                        ResourceLocation.withDefaultNamespace("raw_copper")),
                new Source(id("mi_macerator"), json("{\"type\":\"modern_industrialization:macerator\",\"item_inputs\":[{\"amount\":1,\"item\":\"mekanism:dust_copper\"}],"
                        + "\"item_outputs\":[{\"amount\":2,\"item\":\"mekanism:dust_copper\"}]}"), mekDust));

        PackContent.Content plan = RecipeRewrites.plan(sources, conversions, SHIPPED);
        assertTrue(plan.files().get("data/test/recipes/ie_crusher.json").toString().contains("\"id\":\"modern_industrialization:copper_dust\""));
        assertFalse(plan.files().get("data/test/recipes/mi_macerator.json").toString().contains("mekanism:dust_copper"));

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
