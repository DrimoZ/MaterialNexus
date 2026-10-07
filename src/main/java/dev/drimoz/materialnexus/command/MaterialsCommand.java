package dev.drimoz.materialnexus.command;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import dev.drimoz.materialnexus.network.PacketDistributor;

/** {@code /materials} and the single server-side gate for opening the screen (ADR-013). */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
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
        var untagged = untagged(snapshot);
        java.nio.file.Path file = dev.drimoz.materialnexus.datapack.MnxPaths.root().resolve("report.md");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, dev.drimoz.materialnexus.diagnostics.DiagnosticsReport.build(snapshot, missing, untagged),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable("message.materialnexus.report_failed", e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.materialnexus.report_written", file.toString()), false);
        return 1;
    }

    /** Item name shapes for known materials that discovery did not find (MNX-041), for the report and the GUI. */
    public static java.util.List<dev.drimoz.materialnexus.datapack.FormPatterns.Candidate> untagged(
            dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot snapshot) {
        java.util.Set<String> names = new java.util.HashSet<>(dev.drimoz.materialnexus.datapack.MaterialDefinitions.aliases().keySet());
        snapshot.materials().keySet().forEach(m -> names.add(m.name()));
        java.util.Set<net.minecraft.resources.ResourceLocation> discovered = new java.util.HashSet<>();
        snapshot.discovered().materials().values().forEach(forms -> forms.values().forEach(list -> list.forEach(p -> discovered.add(p.resource()))));
        return dev.drimoz.materialnexus.datapack.FormPatterns.candidates(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet(), names, discovered, 3);
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
