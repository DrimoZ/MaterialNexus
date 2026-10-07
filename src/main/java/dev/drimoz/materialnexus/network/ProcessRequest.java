package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the process rule of one form and the routes that already exist for it (MNX-036). */
public record ProcessRequest(String form) implements CustomPacketPayload {
    public static final Type<ProcessRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "process_request"));
    public static final StreamCodec<FriendlyByteBuf, ProcessRequest> STREAM_CODEC = StreamCodec.of(
            (buf, req) -> buf.writeUtf(req.form(), NexusQueries.MAX_QUERY),
            buf -> new ProcessRequest(buf.readUtf(NexusQueries.MAX_QUERY)));

    @Override public Type<ProcessRequest> type() { return TYPE; }
}
