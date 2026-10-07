package dev.drimoz.materialnexus.network;

import net.minecraft.server.level.ServerPlayer;

/** Forge 1.20.1 port: the two sends the mod makes, with 1.21's names, over {@link MnxNetwork}'s channel. */
public final class PacketDistributor {
    private PacketDistributor() { }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        MnxNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        MnxNetwork.CHANNEL.sendToServer(payload);
    }
}
