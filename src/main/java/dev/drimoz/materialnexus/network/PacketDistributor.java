package dev.drimoz.materialnexus.network;

import net.minecraft.server.level.ServerPlayer;

/** Forge 1.20.1 port: the two sends the mod makes, with 1.21's names, over {@link MnxNetwork}'s channel (split when too large). */
public final class PacketDistributor {
    private PacketDistributor() { }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        MnxNetwork.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        MnxNetwork.sendToServer(payload);
    }
}
