package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: restore the policy that was active before the last apply. */
public record RevertRequest() implements CustomPacketPayload {
    public static final RevertRequest INSTANCE = new RevertRequest();
    public static final Type<RevertRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "revert_request"));
    public static final StreamCodec<ByteBuf, RevertRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<RevertRequest> type() { return TYPE; }
}
