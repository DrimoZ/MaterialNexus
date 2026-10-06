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

    /**
     * In "Tools and Utilities", but only for players with operator permissions. Not the vanilla
     * Operator tab: that one stays hidden unless the "Operator Items Tab" option is turned on.
     */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES && event.hasPermissions()) {
            event.accept(NEXUS_TERMINAL.get());
        }
    }
}
