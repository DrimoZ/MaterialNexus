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

/** Server side of Preview / Apply (ADR-009). Stateless: the client sends its pending changes each time. */
final class PreviewHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean APPLYING = new AtomicBoolean();

    private PreviewHandler() { }

    static void handle(ServerPlayer player, PreviewRequest request) {
        List<PolicyEditor.Entry> entries = PolicyEditor.preview(SnapshotManager.current(), request.changes());
        if (!request.apply()) {
            PacketDistributor.sendToPlayer(player, new PreviewPayload(entries));
            return;
        }
        MinecraftServer server = player.server;
        if (server.isDedicatedServer()) {
            player.sendSystemMessage(Component.translatable("message.materialnexus.read_only_apply"));
            return;
        }
        long valid = entries.stream().filter(PolicyEditor.Entry::valid).count();
        if (valid == 0 || !APPLYING.compareAndSet(false, true)) return;

        try {
            PolicyEditor.apply(MnxPaths.policies(), entries);
            // Canonical choices live in the policy; the pack is regenerated so it always matches it.
            GeneratedPack.write(MnxPaths.generated(), Map.of(), new JsonArray());
        } catch (IOException | RuntimeException e) {
            APPLYING.set(false);
            LOGGER.error("Material Nexus apply failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            APPLYING.set(false);
            if (error != null) {
                LOGGER.error("Material Nexus reload after apply failed", error);
                player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", error.getMessage()));
                return;
            }
            player.sendSystemMessage(Component.translatable("message.materialnexus.applied", valid));
            PacketDistributor.sendToPlayer(player, new OpenNexusPayload(false));
        }));
    }
}
