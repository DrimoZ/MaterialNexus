package dev.drimoz.materialnexus.item;

import dev.drimoz.materialnexus.command.MaterialsCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Opens the Material Nexus screen. Holding it grants nothing: the server re-checks permission. */
public final class NexusTerminalItem extends Item {
    public NexusTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) MaterialsCommand.tryOpen(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
