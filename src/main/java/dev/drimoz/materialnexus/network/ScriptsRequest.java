package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: what scripts decide (MNX-076), answered by {@link ScriptsPayload}. */
public record ScriptsRequest() implements CustomPacketPayload {
    public static final ScriptsRequest INSTANCE = new ScriptsRequest();
    public static final Type<ScriptsRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "scripts_request"));
    public static final StreamCodec<ByteBuf, ScriptsRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<ScriptsRequest> type() { return TYPE; }
}
