package dev.drimoz.materialnexus.conversion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Alternative to canonical item for the applied unification (ADR-015). Swapped atomically on each
 * reload; the gameplay path is one identity lookup. No scanning, no index, no history.
 */
public final class ItemConversions {
    private static volatile Map<Item, Item> active = Map.of();
    private static volatile boolean viewerHiding = true;

    private ItemConversions() { }

    /** Unknown ids (a mod removed since the apply) are skipped. */
    public static void install(Map<ResourceLocation, ResourceLocation> byId) {
        Map<Item, Item> items = new IdentityHashMap<>();
        byId.forEach((from, to) -> BuiltInRegistries.ITEM.getOptional(from).ifPresent(f ->
                BuiltInRegistries.ITEM.getOptional(to).ifPresent(t -> items.put(f, t))));
        active = Collections.unmodifiableMap(items);
    }

    /** Whether recipe viewers should hide alternatives: off when Almost Unified owns viewer hiding (ADR-012). */
    public static void setViewerHiding(boolean enabled) {
        viewerHiding = enabled;
    }

    /** The alternatives currently converted, for recipe viewers on the client. */
    public static java.util.List<ResourceLocation> alternatives() {
        if (!viewerHiding) return java.util.List.of();
        return active.keySet().stream().map(BuiltInRegistries.ITEM::getKey).sorted().toList();
    }

    public static boolean isEmpty() {
        return active.isEmpty();
    }

    /** The canonical stack keeping count and components, or the same stack when nothing applies. */
    public static ItemStack convert(ItemStack stack) {
        Item to = active.get(stack.getItem());
        return to == null ? stack : stack.transmuteCopy(to, stack.getCount());
    }

    /** Converts every slot of a container in place; returns how many stacks changed. */
    public static int convert(Container container) {
        int changed = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            ItemStack converted = convert(stack);
            if (converted != stack) {
                container.setItem(i, converted);
                changed++;
            }
        }
        if (changed > 0) container.setChanged();
        return changed;
    }
}
