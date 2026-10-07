package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: one page of the material (or form, MNX-035) list, filtered by name and by status (MNX-049):
 * {@link NexusQueries#ALL}, {@link NexusQueries#TO_DECIDE}, {@link NexusQueries#UNIFIED}.
 */
public record MaterialListRequest(int page, String query, boolean byForm, int status) implements CustomPacketPayload {
    public static final Type<MaterialListRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_list_request"));
    public static final StreamCodec<FriendlyByteBuf, MaterialListRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> { buf.writeVarInt(req.page()); buf.writeUtf(req.query(), NexusQueries.MAX_QUERY); buf.writeBoolean(req.byForm()); buf.writeVarInt(req.status()); },
            buf -> new MaterialListRequest(buf.readVarInt(), buf.readUtf(NexusQueries.MAX_QUERY), buf.readBoolean(), buf.readVarInt()));

    @Override public Type<MaterialListRequest> type() { return TYPE; }
}
