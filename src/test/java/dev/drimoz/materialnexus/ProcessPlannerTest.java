package dev.drimoz.materialnexus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.core.policy.ProcessRules.Route;
import dev.drimoz.materialnexus.core.policy.ProcessRules.Rule;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.ProcessPlanner;
import dev.drimoz.materialnexus.datapack.RecipeRewrites.Source;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Process rules generate balance: every generated recipe must carry exactly the requested ratio, or not exist. */
class ProcessPlannerTest {
    private static final FormId ROD = new FormId("rod");
    private static final FormId PLATE = new FormId("plate");
    private static final FormId INGOT = new FormId("ingot");
    private static final MaterialId ALUMINUM = new MaterialId("aluminum");
    private static final MaterialId IRON = new MaterialId("iron");

    private static ResourceLocation rl(String id) { return ResourceLocation.parse(id); }

    private static Source source(String id, String json) {
        return new Source(rl(id), Optional.of(JsonParser.parseString(json).getAsJsonObject()), rl(id));
    }

    // Real shapes from IE 12.4, Create 6 and vanilla.
    private static final List<Source> SOURCES = List.of(
            source("immersiveengineering:metalpress/rod_iron", """
                    {"conditions":[{"type":"neoforge:not","value":{"type":"neoforge:tag_empty","tag":"forge:rods/iron"}}],
                     "type":"immersiveengineering:metal_press","energy":2400,"input":{"tag":"forge:ingots/iron"},
                     "mold":"immersiveengineering:mold_rod","result":{"basePredicate":{"tag":"forge:rods/iron"},"count":2}}"""),
            source("create:pressing/iron_ingot", """
                    {"type":"create:pressing","ingredients":[{"tag":"forge:ingots/iron"}],"results":[{"item":"create:iron_sheet"}]}"""),
            source("immersiveengineering:crafting/stick_iron", """
                    {"type":"minecraft:crafting_shaped","category":"misc","key":{"i":{"tag":"forge:ingots/iron"}},"pattern":["i","i"],
                     "result":{"count":4,"item":"immersiveengineering:stick_iron"}}"""),
            source("test:cut_iron_rod", """
                    {"type":"minecraft:stonecutting","ingredient":{"tag":"forge:ingots/iron"},"result":{"count":2,"item":"immersiveengineering:stick_iron"}}"""));

    private static PackContent.Content plan(Map<FormId, Rule> rules) {
        Map<ResourceLocation, List<ResourceLocation>> tags = Map.of(
                rl("forge:ingots/iron"), List.of(rl("minecraft:iron_ingot")),
                rl("forge:rods/iron"), List.of(rl("immersiveengineering:stick_iron")),
                rl("forge:plates/iron"), List.of(rl("create:iron_sheet")),
                rl("forge:ingots/aluminum"), List.of(rl("immersiveengineering:ingot_aluminum")),
                rl("forge:rods/aluminum"), List.of(rl("immersiveengineering:stick_aluminum")),
                rl("forge:plates/aluminum"), List.of(rl("immersiveengineering:plate_aluminum")));
        ResolutionPolicy policy = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(),
                AlmostUnified.NONE, new ProcessRules(rules, Map.of()));
        var resolved = CanonicalResolver.resolve(TagDiscovery.discover(tags), policy);
        return ProcessPlanner.plan(SOURCES, RecipeRewritesTest.SHIPPED, resolved, policy);
    }

    private static String path(MaterialForm target, Route route) {
        return "data/materialnexus/recipes/" + ProcessPlanner.recipeId(target, route).getPath() + ".json";
    }

    private static JsonObject file(PackContent.Content content, MaterialForm target, Route route) {
        var json = content.files().get(path(target, route));
        assertNotNull(json, path(target, route));
        return json.getAsJsonObject();
    }

    private static dev.drimoz.materialnexus.datapack.ProcessTemplates.Template shippedTemplate(String name) {
        try (var in = ProcessPlannerTest.class.getResourceAsStream("/data/materialnexus/material_nexus/process_templates/" + name + ".json")) {
            assertNotNull(in, name);
            return dev.drimoz.materialnexus.datapack.ProcessTemplates.parse(JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))).orElseThrow();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** MNX-037: what no example can carry (a 3-ingot pattern, a gear nobody presses yet) comes from a template, still exact. */
    @Test
    void templatesCoverWhatExamplesCannot() {
        Route shaped = new Route(rl("minecraft:crafting_shaped"), INGOT, 3, 4);
        Route gear = new Route(rl("immersiveengineering:metal_press"), INGOT, 2, 1);
        Map<ResourceLocation, List<ResourceLocation>> tags = Map.of(
                rl("forge:ingots/iron"), List.of(rl("minecraft:iron_ingot")),
                rl("forge:rods/iron"), List.of(rl("immersiveengineering:stick_iron")),
                rl("forge:ingots/aluminum"), List.of(rl("immersiveengineering:ingot_aluminum")),
                rl("forge:rods/aluminum"), List.of(rl("immersiveengineering:stick_aluminum")),
                rl("forge:gears/aluminum"), List.of(rl("immersiveengineering:gear_aluminum")));
        ResolutionPolicy policy = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), AlmostUnified.NONE,
                new ProcessRules(Map.of(ROD, new Rule(List.of(shaped), false, false), new FormId("gear"), new Rule(List.of(gear), false, false)), Map.of()));
        var resolved = CanonicalResolver.resolve(TagDiscovery.discover(tags), policy);
        var content = ProcessPlanner.plan(SOURCES, RecipeRewritesTest.SHIPPED, resolved, policy,
                List.of(shippedTemplate("minecraft_crafting_shaped"), shippedTemplate("immersiveengineering_metal_press")), tag -> true);

        JsonObject rod = file(content, new MaterialForm(ALUMINUM, ROD), shaped);
        assertEquals(3, rod.getAsJsonArray("pattern").size());
        assertEquals("forge:ingots/aluminum", rod.getAsJsonObject("key").getAsJsonObject("#").get("tag").getAsString());
        assertEquals(4, rod.getAsJsonObject("result").get("count").getAsInt());

        JsonObject press = file(content, new MaterialForm(ALUMINUM, new FormId("gear")), gear);
        assertEquals("immersiveengineering:mold_gear", press.get("mold").getAsString());
        assertEquals(2, press.getAsJsonObject("input").get("count").getAsInt());
        assertEquals("immersiveengineering:gear_aluminum", press.getAsJsonObject("result").getAsJsonObject("basePredicate").get("item").getAsString());
    }

    @Test
    void copiesAnotherMaterialsRecipeWithTheRequestedRatio() {
        Route press = new Route(rl("immersiveengineering:metal_press"), INGOT, 1, 2);
        Route shaped = new Route(rl("minecraft:crafting_shaped"), INGOT, 2, 4);
        Route shapedOtherRatio = new Route(rl("minecraft:crafting_shaped"), INGOT, 3, 4);
        Route pressing = new Route(rl("create:pressing"), INGOT, 2, 1);
        var content = plan(Map.of(ROD, new Rule(List.of(press, shaped, shapedOtherRatio), true, false),
                PLATE, new Rule(List.of(pressing), false, true)));
        MaterialForm aluRod = new MaterialForm(ALUMINUM, ROD);

        // IE: tags swapped everywhere (conditions too), count kept on the basePredicate wrapper, mold untouched.
        JsonObject ie = file(content, aluRod, press);
        assertEquals("forge:ingots/aluminum", ie.getAsJsonObject("input").get("tag").getAsString());
        assertEquals("forge:rods/aluminum", ie.getAsJsonObject("result").getAsJsonObject("basePredicate").get("tag").getAsString());
        assertEquals(2, ie.getAsJsonObject("result").get("count").getAsInt());
        assertFalse(ie.toString().contains("iron"), ie.toString());
        assertEquals("immersiveengineering:mold_rod", ie.get("mold").getAsString());

        // Shaped crafting with the same ratio: copied; the canonical aluminum rod is the output.
        assertEquals("immersiveengineering:stick_aluminum", file(content, aluRod, shaped).getAsJsonObject("result").get("item").getAsString());
        // A pattern cannot be resized safely: reported, not guessed.
        assertTrue(content.effects().contains(new PackContent.Effect(ProcessPlanner.UNSUPPORTED, rl("minecraft:crafting_shaped"),
                rl("immersiveengineering:stick_aluminum"))));
        assertFalse(content.files().containsKey(path(aluRod, shapedOtherRatio)));

        // Create: 2 ingots → 1 plate is the ingredient listed twice; the output is an item stack.
        JsonObject create = file(content, new MaterialForm(ALUMINUM, PLATE), pressing);
        assertEquals(2, create.getAsJsonArray("ingredients").size());
        assertEquals("immersiveengineering:plate_aluminum", create.getAsJsonArray("results").get(0).getAsJsonObject().get("item").getAsString());

        // enforce_ratio: iron's own 1 → 1 pressing is replaced by a 2 → 1 copy of itself.
        assertTrue(content.effects().contains(new PackContent.Effect(ProcessPlanner.DISABLE, rl("create:pressing/iron_ingot"), rl("create:iron_sheet"))));
        assertEquals(2, file(content, new MaterialForm(IRON, PLATE), pressing).getAsJsonArray("ingredients").size());

        // exclusive rods: iron's stonecutting is not a route of the rule, so it goes; its press and crafting stay.
        assertTrue(content.effects().contains(new PackContent.Effect(ProcessPlanner.DISABLE, rl("test:cut_iron_rod"), rl("immersiveengineering:stick_iron"))));
        assertEquals(2, content.effects().stream().filter(e -> e.kind().equals(ProcessPlanner.DISABLE)).count());
        // Iron already has its press and its 2 → 4 crafting: nothing generated (rods do not enforce ratios).
        assertFalse(content.effects().stream().anyMatch(e -> e.kind().equals(ProcessPlanner.PROCESS) && e.target().getPath().startsWith("process/rod/iron/")));
    }
}
