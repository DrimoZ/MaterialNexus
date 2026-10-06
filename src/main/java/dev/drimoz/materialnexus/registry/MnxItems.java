package dev.drimoz.materialnexus.registry;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.item.NexusTerminalItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MnxItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaterialNexus.MOD_ID);

    public static final DeferredItem<NexusTerminalItem> NEXUS_TERMINAL =
            ITEMS.register("nexus_terminal", () -> new NexusTerminalItem(new Item.Properties().stacksTo(1)));

    private MnxItems() { }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(MnxItems::addToCreativeTab);
    }

    /** Only in the vanilla "Operator Utilities" tab, which the game shows to operators only. */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(NEXUS_TERMINAL.get());
        }
    }
}
