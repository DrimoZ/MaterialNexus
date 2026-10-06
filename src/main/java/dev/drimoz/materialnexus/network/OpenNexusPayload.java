package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server to client, sent once when an authorized player opens Material Nexus. Not a state stream. */
public record OpenNexusPayload(boolean readOnly, boolean canRevert) implements CustomPacketPayload {
    public static final Type<OpenNexusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "open_nexus"));
    public static final StreamCodec<ByteBuf, OpenNexusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenNexusPayload::readOnly,
            ByteBufCodecs.BOOL, OpenNexusPayload::canRevert,
            OpenNexusPayload::new);

    /** Editing is a singleplayer/LAN activity; dedicated servers only get read-only diagnostics (ADR-013). */
    public static OpenNexusPayload forPlayer(ServerPlayer player) {
        boolean readOnly = player.server.isDedicatedServer();
        return new OpenNexusPayload(readOnly, !readOnly && PolicyEditor.canRevert(MnxPaths.policies()));
    }

    @Override public Type<OpenNexusPayload> type() { return TYPE; }
}
