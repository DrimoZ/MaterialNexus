package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the text of one editable data file (MNX-046). */
public record DataReadRequest(String kind, Identifier id) implements CustomPacketPayload {
    public static final Type<DataReadRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MaterialNexus.MOD_ID, "data_read_request"));
    public static final StreamCodec<FriendlyByteBuf, DataReadRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> { buf.writeUtf(r.kind(), NexusQueries.MAX_QUERY); buf.writeIdentifier(r.id()); },
            buf -> new DataReadRequest(buf.readUtf(NexusQueries.MAX_QUERY), buf.readIdentifier()));

    @Override public Type<DataReadRequest> type() { return TYPE; }
}
