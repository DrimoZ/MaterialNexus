package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the editable data list and the untagged-form audit (MNX-046). */
public record DataListRequest() implements CustomPacketPayload {
    public static final DataListRequest INSTANCE = new DataListRequest();
    public static final Type<DataListRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "data_list_request"));
    public static final StreamCodec<FriendlyByteBuf, DataListRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<DataListRequest> type() { return TYPE; }
}
