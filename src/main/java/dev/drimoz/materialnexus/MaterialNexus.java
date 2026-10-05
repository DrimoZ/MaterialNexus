package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.command.MaterialsCommand;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Entry point for Material Nexus. */
@Mod(MaterialNexus.MOD_ID)
public final class MaterialNexus {
    public static final String MOD_ID = "materialnexus";

    public MaterialNexus(IEventBus modBus, ModContainer modContainer) {
        SnapshotManager.initialize();
        MaterialsCommand.initialize();
    }
}
