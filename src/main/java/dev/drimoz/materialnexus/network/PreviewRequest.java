package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Client to server: pending canonical choices, either to preview or (with {@code apply}) to write. */
public record PreviewRequest(List<CanonicalChange> changes, boolean apply) implements CustomPacketPayload {
    public static final Type<PreviewRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "preview_request"));
    public static final StreamCodec<FriendlyByteBuf, PreviewRequest> STREAM_CODEC =
            StreamCodec.of(PreviewRequest::write, PreviewRequest::read);

    public PreviewRequest {
        changes = List.copyOf(changes);
    }

    @Override public Type<PreviewRequest> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, PreviewRequest req) {
        buf.writeVarInt(req.changes().size());
        for (CanonicalChange c : req.changes()) {
            buf.writeUtf(c.material(), NexusQueries.MAX_QUERY);
            buf.writeUtf(c.form(), NexusQueries.MAX_QUERY);
            buf.writeResourceLocation(c.provider());
        }
        buf.writeBoolean(req.apply());
    }

    private static PreviewRequest read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > PolicyEditor.MAX_CHANGES) throw new DecoderException("Too many changes: " + count);
        List<CanonicalChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            changes.add(new CanonicalChange(buf.readUtf(NexusQueries.MAX_QUERY), buf.readUtf(NexusQueries.MAX_QUERY), buf.readResourceLocation()));
        }
        return new PreviewRequest(changes, buf.readBoolean());
    }
}
