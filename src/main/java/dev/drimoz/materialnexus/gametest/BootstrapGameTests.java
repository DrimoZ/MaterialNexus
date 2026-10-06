package dev.drimoz.materialnexus.gametest;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
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
}
