package dev.drimoz.materialnexus.registry;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.item.NexusTerminalItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MnxItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MaterialNexus.MOD_ID);

    public static final RegistryObject<NexusTerminalItem> NEXUS_TERMINAL =
            ITEMS.register("nexus_terminal", () -> new NexusTerminalItem(new Item.Properties().stacksTo(1)));

    /** Items created for missing forms (MNX-039), read once from config at startup. */
    public static final java.util.List<RegistryObject<dev.drimoz.materialnexus.item.CreatedItem>> CREATED = new java.util.ArrayList<>();

    private MnxItems() { }

    public static void register(IEventBus modBus) {
        for (var entry : dev.drimoz.materialnexus.item.CreatedItems.load(
                dev.drimoz.materialnexus.datapack.MnxPaths.root().resolve(dev.drimoz.materialnexus.item.CreatedItems.FILE))) {
            CREATED.add(ITEMS.register(entry.id().getPath(), () -> new dev.drimoz.materialnexus.item.CreatedItem(entry)));
        }
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
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) CREATED.forEach(item -> event.accept(item.get()));
    }
}
