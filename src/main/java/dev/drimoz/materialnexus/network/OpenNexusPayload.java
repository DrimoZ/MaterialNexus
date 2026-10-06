package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server to client, sent once when an authorized player opens Material Nexus. Not a state stream. */
public record OpenNexusPayload(boolean readOnly, int materialCount) implements CustomPacketPayload {
    public static final Type<OpenNexusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "open_nexus"));
    public static final StreamCodec<ByteBuf, OpenNexusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenNexusPayload::readOnly,
            ByteBufCodecs.VAR_INT, OpenNexusPayload::materialCount,
            OpenNexusPayload::new);

    @Override public Type<OpenNexusPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        // The lambda body only runs on the client, so ClientHooks is never loaded on a dedicated server.
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> ClientHooks.openNexus(payload));
    }
}
