package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server (MNX-064): put back the policy kept with a history entry. The id is checked server-side. */
public record RestoreRequest(String snapshot) implements CustomPacketPayload {
    public static final Type<RestoreRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "restore_request"));
    public static final StreamCodec<FriendlyByteBuf, RestoreRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> buf.writeUtf(req.snapshot(), 32), buf -> new RestoreRequest(buf.readUtf(32)));

    @Override public Type<RestoreRequest> type() { return TYPE; }
}
