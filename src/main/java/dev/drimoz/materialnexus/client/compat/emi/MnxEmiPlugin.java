package dev.drimoz.materialnexus.client.compat.emi;

import dev.drimoz.materialnexus.client.UnifiedItemsClient;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * EMI adapter (MNX-014): hides the alternatives of the applied unification. EMI calls {@link #register}
 * on each of its reloads, which follow the server data sync that carries the list. Only EMI
 * instantiates this class, so nothing here loads when EMI is absent.
 */
@EmiEntrypoint
public final class MnxEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        var alternatives = UnifiedItemsClient.alternatives();
        if (alternatives.isEmpty()) return;
        registry.removeEmiStacks(stack -> {
            ItemStack item = stack.getItemStack();
            return !item.isEmpty() && alternatives.contains(BuiltInRegistries.ITEM.getKey(item.getItem()));
        });
    }
}
