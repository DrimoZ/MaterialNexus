package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.network.MnxNetwork;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Entry point for Material Nexus. */
@Mod(MaterialNexus.MOD_ID)
public final class MaterialNexus {
    public static final String MOD_ID = "materialnexus";

    public MaterialNexus() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MnxItems.register(modBus);
        MnxNetwork.register();
        modBus.addListener(GeneratedPack::onAddPackFinders);
    }
}
