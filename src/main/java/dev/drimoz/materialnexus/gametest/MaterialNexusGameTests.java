package dev.drimoz.materialnexus.gametest;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.command.MaterialsCommand;
import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime acceptance tests; one per critical behavior. */
@GameTestHolder(MaterialNexus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialNexusGameTests {
    private MaterialNexusGameTests() { }

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
