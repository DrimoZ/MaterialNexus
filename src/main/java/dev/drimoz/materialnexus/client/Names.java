package dev.drimoz.materialnexus.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Display names for semantic ids. Forms have lang keys; materials are pack data and fall back to their name. */
final class Names {
    private Names() { }

    static Component material(String name) {
        return Component.translatableWithFallback("materialnexus.material." + name, capitalize(name.replace('_', ' ')));
    }

    static Component form(String name) {
        return Component.translatableWithFallback("materialnexus.form." + name, name);
    }

    static ItemStack stack(ResourceLocation item) {
        return BuiltInRegistries.ITEM.getOptional(item).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** "Tin Ingot (Mekanism)": duplicates usually share their display name (MNX-076). */
    static Component withMod(ResourceLocation item) {
        return Component.empty().append(stack(item).getHoverName()).append(" (" + NexusScreen.modName(item.getNamespace()) + ")");
    }

    static String lowerName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
