package dev.drimoz.materialnexus.gametest;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.command.MaterialsCommand;
import dev.drimoz.materialnexus.conversion.ItemConversions;
import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
import dev.drimoz.materialnexus.datapack.RecipeSources;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime acceptance tests; one per critical behavior. */
@GameTestHolder(MaterialNexus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialNexusGameTests {
    private MaterialNexusGameTests() { }

    /** MNX-015..018: real Create, Mekanism, IE and MI recipes are recognized and rewritten (skipped when the mods are absent). */
    @GameTest(template = "empty")
    public static void createAndMekanismRecipesAreRewritten(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create") || !ModList.get().isLoaded("mekanism")
                || !ModList.get().isLoaded("immersiveengineering") || !ModList.get().isLoaded("modern_industrialization")) {
            helper.succeed();
            return;
        }
        var conversions = java.util.Map.of(
                ResourceLocation.fromNamespaceAndPath("create", "copper_sheet"), ResourceLocation.fromNamespaceAndPath("immersiveengineering", "plate_copper"),
                ResourceLocation.fromNamespaceAndPath("mekanism", "dust_copper"), ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_dust"),
                ResourceLocation.fromNamespaceAndPath("immersiveengineering", "dust_copper"), ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_dust"),
                ResourceLocation.fromNamespaceAndPath("modern_industrialization", "copper_plate"), ResourceLocation.fromNamespaceAndPath("immersiveengineering", "plate_copper"));
        var formats = RecipeFormats.load(helper.getLevel().getServer().getResourceManager());
        var plan = RecipeRewrites.plan(RecipeSources.collect(helper.getLevel().getServer(), conversions, formats, java.util.List.of()), conversions, formats);
        var rewritten = plan.effects().stream().filter(e -> e.kind().equals(RecipeRewrites.REWRITE)).map(e -> e.target().getNamespace()).toList();
        helper.assertTrue(rewritten.contains("create"), "a Create recipe producing the copper sheet should be rewritten, got " + plan.effects());
        helper.assertTrue(rewritten.contains("mekanism"), "a Mekanism recipe producing or consuming copper dust should be rewritten, got " + plan.effects());
        helper.assertTrue(rewritten.contains("immersiveengineering"), "an IE recipe producing copper dust should be rewritten, got " + plan.effects());
        helper.assertTrue(rewritten.contains("modern_industrialization"), "an MI recipe producing the copper plate should be rewritten, got " + plan.effects());
        helper.succeed();
    }

    /** MNX-009: the recipe family of copper ingots lists real recipes producing the canonical item. */
    @GameTest(template = "empty")
    public static void copperIngotRecipeFamilyIsListed(GameTestHelper helper) {
        var form = SnapshotManager.current().materials().get(new MaterialId("copper")).forms().get(new FormId("ingot"));
        var rows = dev.drimoz.materialnexus.datapack.RecipeFamilies.family(helper.getLevel().getServer(), form);
        helper.assertTrue(rows.stream().anyMatch(r -> r.status() == dev.drimoz.materialnexus.datapack.RecipeFamilies.Status.CANONICAL),
                "copper ingot family should contain recipes making the canonical item, got " + rows.size() + " rows");
        helper.succeed();
    }

    /** MNX-019: the five shipped presets are loaded with the data. */
    @GameTest(template = "empty")
    public static void shippedPresetsAreLoaded(GameTestHelper helper) {
        var presets = dev.drimoz.materialnexus.datapack.Presets.all().keySet();
        helper.assertTrue(presets.size() >= 5 && presets.contains(ResourceLocation.fromNamespaceAndPath("materialnexus", "tech_pack")),
                "shipped presets should be loaded, got " + presets);
        helper.succeed();
    }

    /** MNX-033: shipped material definitions are loaded by the data reload, before discovery uses them. */
    @GameTest(template = "empty")
    public static void shippedMaterialDefinitionsAreLoaded(GameTestHelper helper) {
        helper.assertTrue(new MaterialId("aluminum").equals(dev.drimoz.materialnexus.datapack.MaterialDefinitions.aliases().get("aluminium")),
                "the shipped aluminium alias should be loaded, got " + dev.drimoz.materialnexus.datapack.MaterialDefinitions.aliases());
        helper.succeed();
    }

    /** MNX-032: /materials report runs from the server console and writes the analysis. */
    @GameTest(template = "empty")
    public static void reportCommandWritesTheAnalysis(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "materials report");
        var file = MnxPaths.root().resolve("report.md");
        try {
            String report = java.nio.file.Files.readString(file);
            helper.assertTrue(report.contains("## copper") && report.contains("| ingot |"), "report should describe copper ingots");
        } catch (java.io.IOException e) {
            helper.fail("report not written: " + e.getMessage());
        }
        helper.succeed();
    }

    /** MNX-010: iron has every standard conversion in a real pack, so no proposal may appear (no false positives). */
    @GameTest(template = "empty")
    public static void ironHasNoMissingRecipeProposals(GameTestHelper helper) {
        var missing = dev.drimoz.materialnexus.datapack.RecipeEdges.missing(helper.getLevel().getServer(),
                SnapshotManager.current().discovered(), new MaterialId("iron"));
        helper.assertTrue(missing.isEmpty(), "iron should have no missing conversion, got " + missing);
        helper.succeed();
    }

    /** MNX-025: real recipes are read with their JSON from the resource stack and vanilla outputs are rewritten. */
    @GameTest(template = "empty")
    public static void vanillaRecipesProducingAnAlternativeAreRewritten(GameTestHelper helper) {
        ResourceLocation copper = ResourceLocation.withDefaultNamespace("copper_ingot");
        ResourceLocation iron = ResourceLocation.withDefaultNamespace("iron_ingot");
        var formats = RecipeFormats.load(helper.getLevel().getServer().getResourceManager());
        var sources = RecipeSources.collect(helper.getLevel().getServer(), java.util.Map.of(copper, iron), formats, java.util.List.of());
        var plan = RecipeRewrites.plan(sources, java.util.Map.of(copper, iron), formats);
        var fromBlock = new PackContent.Effect(RecipeRewrites.REWRITE, ResourceLocation.withDefaultNamespace("copper_ingot"), iron);
        helper.assertTrue(plan.effects().contains(fromBlock), "minecraft:copper_ingot should be rewritten, got " + plan.effects());
        helper.assertTrue(plan.files().get("data/minecraft/recipe/copper_ingot.json").toString().contains("\"id\":\"minecraft:iron_ingot\""),
                "rewritten JSON must produce the canonical item");
        helper.succeed();
    }

    /**
     * MNX-036: process rules on the real loaded recipes generate recipes the game itself can decode, with the
     * requested ratio. Nuggets everywhere; with Immersive Engineering, rods in its metal press too.
     */
    @GameTest(template = "empty")
    public static void processRulesGenerateLoadableRecipes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var nugget = new dev.drimoz.materialnexus.core.policy.ProcessRules.Route(
                ResourceLocation.withDefaultNamespace("crafting_shapeless"), new FormId("ingot"), 1, 8);
        var rules = new java.util.HashMap<FormId, dev.drimoz.materialnexus.core.policy.ProcessRules.Rule>();
        rules.put(new FormId("nugget"), new dev.drimoz.materialnexus.core.policy.ProcessRules.Rule(java.util.List.of(nugget), false, true));
        if (ModList.get().isLoaded("immersiveengineering")) {
            rules.put(new FormId("rod"), new dev.drimoz.materialnexus.core.policy.ProcessRules.Rule(java.util.List.of(
                    new dev.drimoz.materialnexus.core.policy.ProcessRules.Route(ResourceLocation.fromNamespaceAndPath("immersiveengineering", "metal_press"),
                            new FormId("ingot"), 1, 3)), false, true));
        }
        var policy = dev.drimoz.materialnexus.core.policy.ResolutionPolicy.NONE.withProcesses(rules);
        var formats = RecipeFormats.load(server.getResourceManager());
        var plan = dev.drimoz.materialnexus.datapack.ProcessPlanner.plan(RecipeSources.known(server, formats, java.util.List.of()), formats,
                dev.drimoz.materialnexus.core.resolution.CanonicalResolver.resolve(SnapshotManager.current().discovered(), policy), policy);

        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        var generated = plan.effects().stream().filter(e -> e.kind().equals(dev.drimoz.materialnexus.datapack.ProcessPlanner.PROCESS)).toList();
        helper.assertTrue(generated.stream().anyMatch(e -> e.item().equals(ResourceLocation.withDefaultNamespace("iron_nugget"))),
                "iron nuggets 1 → 8 should be generated, got " + plan.effects());
        for (var e : generated) {
            var json = plan.files().get("data/materialnexus/recipe/" + e.target().getPath() + ".json").getAsJsonObject().deepCopy();
            json.remove("neoforge:conditions");
            var recipe = net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, json);
            helper.assertTrue(recipe.isSuccess(), e.target() + " does not decode: " + recipe.error().map(Object::toString).orElse("") + " " + json);
        }
        var iron = plan.files().get("data/materialnexus/recipe/" + dev.drimoz.materialnexus.datapack.ProcessPlanner.recipeId(
                new dev.drimoz.materialnexus.core.domain.MaterialForm(new MaterialId("iron"), new FormId("nugget")), nugget).getPath() + ".json");
        helper.assertTrue(iron != null && iron.toString().contains("\"count\":8"), "iron nugget recipe must give 8, got " + iron);
        helper.succeed();
    }

    /**
     * MNX-039: an item listed in items.json (fixture: netherite rod) is registered at startup, joins its convention tag
     * through the created-items pack, and discovery sees it as netherite's rod.
     */
    @GameTest(template = "empty")
    public static void createdItemIsRegisteredTaggedAndDiscovered(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "netherite_rod");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id), "netherite_rod should be registered");
        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:rods/netherite"));
        helper.assertTrue(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).is(tag), "netherite_rod should be in #c:rods/netherite");
        var providers = SnapshotManager.current().discovered().providers(new MaterialId("netherite"), new FormId("rod"));
        helper.assertTrue(providers.stream().anyMatch(p -> p.resource().equals(id)), "discovery should see the netherite rod, got " + providers);
        helper.succeed();
    }

    /**
     * MNX-043: with the addon formats, unifying electrum wire on IE's rewrites Create Crafts &amp; Additions' rolling
     * recipe instead of listing it as unsupported.
     */
    @GameTest(template = "empty")
    public static void addonRecipesAreRewritten(GameTestHelper helper) {
        if (!ModList.get().isLoaded("createaddition")) { helper.succeed(); return; }
        var server = helper.getLevel().getServer();
        var conversions = java.util.Map.of(ResourceLocation.parse("createaddition:electrum_wire"), ResourceLocation.parse("immersiveengineering:wire_electrum"));
        var formats = RecipeFormats.load(server.getResourceManager());
        var plan = RecipeRewrites.plan(RecipeSources.collect(server, conversions, formats, java.util.List.of()), conversions, formats);
        helper.assertTrue(plan.effects().stream().noneMatch(e -> e.kind().equals(RecipeRewrites.UNSUPPORTED) && e.target().getNamespace().equals("createaddition")),
                "createaddition recipes should all be handled, got " + plan.effects());
        helper.assertTrue(plan.effects().stream().anyMatch(e -> e.kind().equals(RecipeRewrites.REWRITE) && e.target().getNamespace().equals("createaddition")),
                "the createaddition rolling recipe should be rewritten, got " + plan.effects());
        helper.succeed();
    }

    /**
     * MNX-044: a worst-case Preview on the dev pack (every suggestion accepted, rules on three forms) stays interactive.
     * Logs each step; fails above 5 s, which is when a recipe index becomes worth its complexity.
     */
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void fullPreviewOnTheDevPackIsFast(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var snapshot = SnapshotManager.current();
        long t0 = System.nanoTime();
        var explicit = new java.util.HashMap<dev.drimoz.materialnexus.core.domain.MaterialForm, ResourceLocation>();
        dev.drimoz.materialnexus.network.SuggestionsPayload.of(snapshot).changes().forEach(c -> explicit.put(
                new dev.drimoz.materialnexus.core.domain.MaterialForm(new MaterialId(c.material()), new FormId(c.form())), c.provider()));
        var ingot = new FormId("ingot");
        var rules = new java.util.HashMap<FormId, dev.drimoz.materialnexus.core.policy.ProcessRules.Rule>();
        for (String form : java.util.List.of("rod", "plate", "wire")) {
            rules.put(new FormId(form), new dev.drimoz.materialnexus.core.policy.ProcessRules.Rule(java.util.List.of(
                    new dev.drimoz.materialnexus.core.policy.ProcessRules.Route(ResourceLocation.withDefaultNamespace("crafting_shapeless"), ingot, 1, 2)), true, true));
        }
        var policy = dev.drimoz.materialnexus.core.policy.ResolutionPolicy.NONE.withExplicit(explicit).withProcesses(rules);
        var resolved = dev.drimoz.materialnexus.core.resolution.CanonicalResolver.resolve(snapshot.discovered(), policy);
        long t1 = System.nanoTime();
        var formats = RecipeFormats.load(server.getResourceManager());
        var content = PackContent.full(resolved, policy, false, (conversions, ownership) -> RecipeRewrites.plan(
                RecipeSources.collect(server, conversions, formats, java.util.List.of()), conversions, formats, ownership));
        long t2 = System.nanoTime();
        var known = RecipeSources.known(server, formats, java.util.List.of());
        long t3 = System.nanoTime();
        var processes = dev.drimoz.materialnexus.datapack.ProcessPlanner.plan(known, formats, resolved, policy);
        long t4 = System.nanoTime();
        org.slf4j.LoggerFactory.getLogger("MaterialNexusPerf").info(
                "Preview: {} choices, resolve {} ms, unification+rewrites {} ms ({} effects), read {} recipes {} ms, processes {} ms ({} effects)",
                explicit.size(), (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, content.effects().size(), known.size(), (t3 - t2) / 1_000_000,
                (t4 - t3) / 1_000_000, processes.effects().size());
        helper.assertTrue((t4 - t0) / 1_000_000 < 5000, "full preview took " + (t4 - t0) / 1_000_000 + " ms");
        helper.succeed();
    }

    /**
     * MNX-037: every shipped process template whose machine is loaded, filled for iron, is a recipe the game decodes.
     * A template with a broken shape would otherwise only show up as "recipe_invalid" in a player's Preview.
     */
    @GameTest(template = "empty")
    public static void shippedProcessTemplatesDecode(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        var ingredient = new com.google.gson.JsonObject();
        ingredient.addProperty("tag", "c:ingots/iron");
        int checked = 0;
        for (var template : dev.drimoz.materialnexus.datapack.ProcessTemplates.templates()) {
            if (!ModList.get().isLoaded(template.machine().getNamespace()) && !template.machine().getNamespace().equals("minecraft")) continue;
            String form = template.forms().isEmpty() ? "plate" : template.forms().keySet().iterator().next().name();
            int in = template.patterns().isEmpty() ? 1 : 2;
            var json = dev.drimoz.materialnexus.datapack.ProcessTemplates.fill(template, new dev.drimoz.materialnexus.datapack.ProcessTemplates.Values(
                    "iron", form, ingredient, in, 1, "minecraft:iron_ingot", "c:ingots/iron"));
            helper.assertTrue(json.isPresent(), template.machine() + " template did not fill");
            var decoded = net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, json.get());
            helper.assertTrue(decoded.isSuccess(), template.machine() + " template does not decode: "
                    + decoded.error().map(Object::toString).orElse("") + " " + json.get());
            checked++;
        }
        helper.assertTrue(checked > 0, "no template checked");
        helper.succeed();
    }

    /** MNX-028: an applied alternative becomes the canonical item when it enters the world or a container is converted. */
    @GameTest(template = "empty")
    public static void unifiedItemsAreConvertedWhenTouched(GameTestHelper helper) {
        ItemConversions.install(java.util.Map.of(ResourceLocation.withDefaultNamespace("copper_ingot"), ResourceLocation.withDefaultNamespace("iron_ingot")));
        try {
            var stack = new ItemStack(Items.COPPER_INGOT, 7);
            var dropped = helper.spawnItem(Items.COPPER_INGOT, new net.minecraft.core.BlockPos(0, 1, 0));
            helper.assertTrue(dropped.getItem().is(Items.IRON_INGOT), "dropped alternative should become canonical, got " + dropped.getItem());

            var chest = new net.minecraft.world.SimpleContainer(stack, new ItemStack(Items.GOLD_INGOT, 3));
            helper.assertTrue(ItemConversions.convert(chest) == 1, "only the alternative slot should change");
            helper.assertTrue(chest.getItem(0).is(Items.IRON_INGOT) && chest.getItem(0).getCount() == 7, "count must be kept");
            helper.assertTrue(chest.getItem(1).is(Items.GOLD_INGOT), "unrelated items are untouched");
        } finally {
            ItemConversions.install(java.util.Map.of());
        }
        helper.succeed();
    }

    /** MNX-022: the global generated pack exists on disk and is active in this world, above other packs. */
    @GameTest(template = "empty")
    public static void generatedPackIsInjectedIntoTheWorld(GameTestHelper helper) {
        var selected = helper.getLevel().getServer().getPackRepository().getSelectedIds();
        helper.assertTrue(selected.contains(GeneratedPack.PACK_ID), "generated pack is not enabled: " + selected);
        helper.assertTrue(java.nio.file.Files.exists(MnxPaths.generated().resolve(GeneratedPack.MARKER)), "generated pack marker missing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modLoadsWithSnapshot(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(MaterialNexus.MOD_ID), "Material Nexus is not loaded");
        helper.assertTrue(SnapshotManager.current() != null, "No active snapshot");
        helper.succeed();
    }

    /** MNX-003: discovery runs at server data load and reads the real convention tags. */
    @GameTest(template = "empty")
    public static void discoversVanillaCopperFromConventionTags(GameTestHelper helper) {
        DiscoveredMaterials discovered = SnapshotManager.current().discovered();
        MaterialId copper = new MaterialId("copper");

        helper.assertTrue(discovered.providers(copper, new FormId("ingot")).stream()
                .anyMatch(p -> p.resource().equals(ResourceLocation.withDefaultNamespace("copper_ingot"))),
                "copper/ingot should include minecraft:copper_ingot");
        helper.assertTrue(discovered.providers(copper, new FormId("raw_block")).stream()
                .anyMatch(p -> p.resource().equals(ResourceLocation.withDefaultNamespace("raw_copper_block"))),
                "c:storage_blocks/raw_copper should map to copper/raw_block");
        helper.assertFalse(discovered.materials().containsKey(new MaterialId("raw_copper")),
                "raw_copper must not be discovered as its own material");
        // MNX-027: host-rock variants are separate forms, never duplicates of each other.
        ResourceLocation deepslateOre = ResourceLocation.withDefaultNamespace("deepslate_copper_ore");
        helper.assertTrue(discovered.providers(copper, new FormId("deepslate_ore")).stream().anyMatch(p -> p.resource().equals(deepslateOre)),
                "deepslate copper ore should be copper/deepslate_ore");
        helper.assertFalse(discovered.providers(copper, new FormId("ore")).stream().anyMatch(p -> p.resource().equals(deepslateOre)),
                "deepslate copper ore must not be in copper/ore");
        helper.succeed();
    }

    /** MNX-006: a non-operator holding a /give'd Nexus Terminal still cannot open the screen. */
    @GameTest(template = "empty")
    public static void nonOperatorIsRefusedServerSide(GameTestHelper helper) {
        // A fake player, not a mock: a mock "logs in", and installed mods (Jade, Mekanism) then try to sync to it.
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MnxItems.NEXUS_TERMINAL.get()));
        helper.assertFalse(player.hasPermissions(MaterialsCommand.PERMISSION_LEVEL), "fake player should not be an operator");
        helper.assertFalse(MaterialsCommand.tryOpen(player), "non-operator must be refused");
        helper.succeed();
    }
}
