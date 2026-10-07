package dev.drimoz.materialnexus.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** An item from {@code items.json} (MNX-039): named "Netherite Rod" from the material and form, tinted on the client. */
public final class CreatedItem extends Item {
    private final CreatedItems.Entry entry;

    public CreatedItem(CreatedItems.Entry entry) {
        super(new Item.Properties());
        this.entry = entry;
    }

    public CreatedItems.Entry entry() { return entry; }

    @Override
    public Component getName(ItemStack stack) {
        String material = entry.material().name();
        String readable = Character.toUpperCase(material.charAt(0)) + material.substring(1).replace('_', ' ');
        return Component.translatable("item.materialnexus.created",
                Component.translatableWithFallback("materialnexus.material." + material, readable),
                Component.translatableWithFallback("materialnexus.form." + entry.form().name(), entry.form().name()));
    }
}
