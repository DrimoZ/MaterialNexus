package dev.drimoz.materialnexus.network;

import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server side of Preview / Apply / Revert (ADR-009). Stateless: the client sends its pending changes each time. */
final class PolicyHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private interface PolicyWrite {
        void run() throws IOException;
    }

    private PolicyHandler() { }

    static void preview(ServerPlayer player, PreviewRequest request) {
        List<PolicyEditor.Entry> entries = PolicyEditor.preview(SnapshotManager.current(), request.changes());
        if (!request.apply()) {
            PacketDistributor.sendToPlayer(player, new PreviewPayload(entries));
            return;
        }
        long valid = entries.stream().filter(PolicyEditor.Entry::valid).count();
        if (valid == 0) return;
        writeAndReload(player, () -> PolicyEditor.apply(MnxPaths.policies(), entries),
                Component.translatable("message.materialnexus.applied", valid));
    }

    static void revert(ServerPlayer player) {
        if (!PolicyEditor.canRevert(MnxPaths.policies())) return;
        writeAndReload(player, () -> PolicyEditor.revert(MnxPaths.policies()),
                Component.translatable("message.materialnexus.reverted"));
    }

    /** Write the policy, regenerate the pack so it always matches it, reload, then reopen the GUI. */
    private static void writeAndReload(ServerPlayer player, PolicyWrite write, Component success) {
        MinecraftServer server = player.server;
        if (server.isDedicatedServer()) {
            player.sendSystemMessage(Component.translatable("message.materialnexus.read_only_apply"));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;

        try {
            write.run();
            GeneratedPack.write(MnxPaths.generated(), Map.of(), new JsonArray());
        } catch (IOException | RuntimeException e) {
            BUSY.set(false);
            LOGGER.error("Material Nexus policy write failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            BUSY.set(false);
            if (error != null) {
                LOGGER.error("Material Nexus reload failed", error);
                player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", error.getMessage()));
                return;
            }
            player.sendSystemMessage(success);
            PacketDistributor.sendToPlayer(player, OpenNexusPayload.forPlayer(player));
        }));
    }
}
