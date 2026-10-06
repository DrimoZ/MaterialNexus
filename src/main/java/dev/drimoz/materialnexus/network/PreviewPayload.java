package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: the validated diff for a {@link PreviewRequest}. */
public record PreviewPayload(List<PolicyEditor.Entry> entries) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "preview"));
    public static final StreamCodec<FriendlyByteBuf, PreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeCollection(p.entries(), (b, e) -> {
                b.writeUtf(e.material());
                b.writeUtf(e.form());
                b.writeOptional(e.from(), FriendlyByteBuf::writeResourceLocation);
                b.writeResourceLocation(e.to());
                b.writeBoolean(e.valid());
            }),
            buf -> new PreviewPayload(buf.readList(b -> new PolicyEditor.Entry(
                    b.readUtf(), b.readUtf(), b.readOptional(FriendlyByteBuf::readResourceLocation), b.readResourceLocation(), b.readBoolean()))));

    public PreviewPayload {
        entries = List.copyOf(entries);
    }

    @Override public Type<PreviewPayload> type() { return TYPE; }
}
