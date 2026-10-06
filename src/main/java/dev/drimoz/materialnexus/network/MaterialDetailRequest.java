package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: every resolved form of one material. */
public record MaterialDetailRequest(String material) implements CustomPacketPayload {
    public static final Type<MaterialDetailRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_detail_request"));
    public static final StreamCodec<FriendlyByteBuf, MaterialDetailRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> buf.writeUtf(req.material(), NexusQueries.MAX_QUERY),
            buf -> new MaterialDetailRequest(buf.readUtf(NexusQueries.MAX_QUERY)));

    @Override public Type<MaterialDetailRequest> type() { return TYPE; }
}
