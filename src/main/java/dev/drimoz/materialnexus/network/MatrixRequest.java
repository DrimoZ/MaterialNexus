package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the materials × forms grid (MNX-049). */
public record MatrixRequest() implements CustomPacketPayload {
    public static final MatrixRequest INSTANCE = new MatrixRequest();
    public static final Type<MatrixRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "matrix_request"));
    public static final StreamCodec<FriendlyByteBuf, MatrixRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<MatrixRequest> type() { return TYPE; }
}
