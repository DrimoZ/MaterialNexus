package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: one page of the material (or form, MNX-035) list, optionally filtered by name. */
public record MaterialListRequest(int page, String query, boolean byForm) implements CustomPacketPayload {
    public static final Type<MaterialListRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_list_request"));
    public static final StreamCodec<FriendlyByteBuf, MaterialListRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> { buf.writeVarInt(req.page()); buf.writeUtf(req.query(), NexusQueries.MAX_QUERY); buf.writeBoolean(req.byForm()); },
            buf -> new MaterialListRequest(buf.readVarInt(), buf.readUtf(NexusQueries.MAX_QUERY), buf.readBoolean()));

    @Override public Type<MaterialListRequest> type() { return TYPE; }
}
