package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: every current suggestion (Default-level canonical with alternatives), to accept in one go. */
public record SuggestionsRequest() implements CustomPacketPayload {
    public static final SuggestionsRequest INSTANCE = new SuggestionsRequest();
    public static final Type<SuggestionsRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "suggestions_request"));
    public static final StreamCodec<ByteBuf, SuggestionsRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<SuggestionsRequest> type() { return TYPE; }
}
