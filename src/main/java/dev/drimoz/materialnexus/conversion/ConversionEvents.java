package dev.drimoz.materialnexus.conversion;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Converts unified alternatives when the game touches them (ADR-015): items entering the world,
 * a player logging in, a container being opened. Server side only; never walks the world.
 */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class ConversionEvents {
    private ConversionEvents() { }

    /**
     * Tells clients which items are unified away, once per data sync (login, reload), so recipe viewers can
     * hide them. High priority: another mod's listener failing on this event must not prevent it.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent event) {
        var payload = new dev.drimoz.materialnexus.network.UnifiedItemsPayload(ItemConversions.alternatives(), ItemConversions.mapping());
        (event.getPlayer() != null ? java.util.List.of(event.getPlayer()) : event.getPlayerList().getPlayers()).forEach(player -> dev.drimoz.materialnexus.network.PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || ItemConversions.isEmpty() || !(event.getEntity() instanceof ItemEntity item)) return;
        ItemStack converted = ItemConversions.convert(item.getItem());
        if (converted != item.getItem()) item.setItem(converted);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (ItemConversions.isEmpty()) return;
        ItemConversions.convert(event.getEntity().getInventory());
        ItemConversions.convert(event.getEntity().getEnderChestInventory());
    }

    /**
     * Only vanilla-backed containers (block entity containers incl. double chests, player inventory, ender chest).
     * Modded machine slots are left alone: their storage is the mod's business, and the optional
     * conversion recipes cover it.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (ItemConversions.isEmpty()) return;
        Set<Container> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : event.getContainer().slots) {
            Container container = slot.container;
            if ((container instanceof BlockEntity || container instanceof CompoundContainer
                    || container instanceof Inventory || container instanceof PlayerEnderChestContainer)
                    && seen.add(container)) {
                ItemConversions.convert(container);
            }
        }
    }
}
