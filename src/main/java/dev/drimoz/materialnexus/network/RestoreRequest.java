package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server (MNX-064): put back the policy kept with a history entry. The id is checked server-side. */
public record RestoreRequest(String snapshot) implements CustomPacketPayload {
    public static final Type<RestoreRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "restore_request"));
    public static final StreamCodec<ByteBuf, RestoreRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(32).map(RestoreRequest::new, RestoreRequest::snapshot);

    @Override public Type<RestoreRequest> type() { return TYPE; }
}
