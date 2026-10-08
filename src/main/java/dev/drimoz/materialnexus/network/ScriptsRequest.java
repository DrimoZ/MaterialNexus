package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: what scripts decide (MNX-076), answered by {@link ScriptsPayload}. */
public record ScriptsRequest() implements CustomPacketPayload {
    public static final ScriptsRequest INSTANCE = new ScriptsRequest();
    public static final Type<ScriptsRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "scripts_request"));
    public static final StreamCodec<FriendlyByteBuf, ScriptsRequest> STREAM_CODEC = StreamCodec.of((buf, req) -> { }, buf -> INSTANCE);

    @Override public Type<ScriptsRequest> type() { return TYPE; }
}
