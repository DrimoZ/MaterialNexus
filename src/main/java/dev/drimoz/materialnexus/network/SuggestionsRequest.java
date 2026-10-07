package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: every current suggestion (Default-level canonical with alternatives), to accept in one go; with
 * {@code saved}, every saved choice instead, to put back to default (MNX-058).
 */
public record SuggestionsRequest(boolean saved) implements CustomPacketPayload {
    public static final SuggestionsRequest INSTANCE = new SuggestionsRequest(false);
    public static final SuggestionsRequest SAVED = new SuggestionsRequest(true);
    public static final Type<SuggestionsRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "suggestions_request"));
    public static final StreamCodec<FriendlyByteBuf, SuggestionsRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> buf.writeBoolean(req.saved()), buf -> new SuggestionsRequest(buf.readBoolean()));

    @Override public Type<SuggestionsRequest> type() { return TYPE; }
}
