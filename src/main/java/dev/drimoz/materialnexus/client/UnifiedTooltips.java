package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * MNX-054: players of the pack see what unification does without opening the tool: an alternative says which item it
 * becomes, the kept item says it is the one kept. From the applied pack only, sent at each data sync.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID, value = Dist.CLIENT)
public final class UnifiedTooltips {
    private UnifiedTooltips() { }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        ResourceLocation target = UnifiedItemsClient.becomes().get(id);
        if (target != null) {
            event.getToolTip().add(Component.translatable("tooltip.materialnexus.becomes", Names.stack(target).getHoverName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (UnifiedItemsClient.kept(id)) {
            event.getToolTip().add(Component.translatable("tooltip.materialnexus.kept").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
