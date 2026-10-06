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
