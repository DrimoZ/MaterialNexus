package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client, sent once when an authorized player opens Material Nexus. Not a state stream. */
public record OpenNexusPayload(boolean readOnly) implements CustomPacketPayload {
    public static final Type<OpenNexusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "open_nexus"));
    public static final StreamCodec<ByteBuf, OpenNexusPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(OpenNexusPayload::new, OpenNexusPayload::readOnly);

    @Override public Type<OpenNexusPayload> type() { return TYPE; }
}
