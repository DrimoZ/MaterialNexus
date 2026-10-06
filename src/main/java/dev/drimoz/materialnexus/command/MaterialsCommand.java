package dev.drimoz.materialnexus.command;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@code /materials} and the single server-side gate for opening the screen (ADR-013). */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class MaterialsCommand {
    public static final int PERMISSION_LEVEL = 2;

    private MaterialsCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("materials")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(ctx -> tryOpen(ctx.getSource().getPlayerOrException()) ? 1 : 0));
    }

    /** Every entry point (command, item) goes through here; the client is never trusted. */
    public static boolean tryOpen(ServerPlayer player) {
        if (!player.hasPermissions(PERMISSION_LEVEL)) {
            player.sendSystemMessage(Component.translatable("message.materialnexus.no_permission"));
            return false;
        }
        PacketDistributor.sendToPlayer(player, OpenNexusPayload.forPlayer(player));
        return true;
    }
}
