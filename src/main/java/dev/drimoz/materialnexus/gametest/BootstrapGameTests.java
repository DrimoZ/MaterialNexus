package dev.drimoz.materialnexus.gametest;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MNX-001: the mod boots on a dedicated server with no optional mods installed. */
@GameTestHolder(MaterialNexus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private BootstrapGameTests() { }

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
}
