package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.network.MnxNetwork;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Entry point for Material Nexus. */
@Mod(MaterialNexus.MOD_ID)
public final class MaterialNexus {
    public static final String MOD_ID = "materialnexus";

    public MaterialNexus(IEventBus modBus, ModContainer modContainer) {
        MnxItems.register(modBus);
        modBus.addListener(MnxNetwork::register);
    }
}
