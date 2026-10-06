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
                .executes(ctx -> tryOpen(ctx.getSource().getPlayerOrException()) ? 1 : 0)
                .then(Commands.literal("report").executes(ctx -> report(ctx.getSource()))));
    }

    /** {@code /materials report}: writes the analysis to config/materialnexus/report.md (MNX-032). Works from the console too. */
    private static int report(net.minecraft.commands.CommandSourceStack source) {
        var snapshot = dev.drimoz.materialnexus.core.resolution.SnapshotManager.current();
        var missing = dev.drimoz.materialnexus.datapack.RecipeEdges.missingAll(source.getServer(), snapshot.discovered());
        java.nio.file.Path file = dev.drimoz.materialnexus.datapack.MnxPaths.root().resolve("report.md");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, dev.drimoz.materialnexus.diagnostics.DiagnosticsReport.build(snapshot, missing),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable("message.materialnexus.report_failed", e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.materialnexus.report_written", file.toString()), false);
        return 1;
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
