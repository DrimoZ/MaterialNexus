package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the validated diff for a {@link PreviewRequest}: canonical changes, plus what the
 * generated pack would gain ({@code added}) or lose ({@code removed}) compared to the current one.
 */
public record PreviewPayload(List<PolicyEditor.Entry> entries, List<PackContent.Effect> added, List<PackContent.Effect> removed,
                             java.util.Optional<ResourceLocation> preset)
        implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "preview"));
    public static final StreamCodec<FriendlyByteBuf, PreviewPayload> STREAM_CODEC =
            StreamCodec.of(PreviewPayload::write, PreviewPayload::read);

    public PreviewPayload {
        entries = List.copyOf(entries);
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    @Override public Type<PreviewPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, PreviewPayload p) {
        buf.writeCollection(p.entries(), (b, e) -> {
            b.writeUtf(e.material());
            b.writeUtf(e.form());
            b.writeOptional(e.from(), FriendlyByteBuf::writeResourceLocation);
            b.writeResourceLocation(e.to());
            b.writeBoolean(e.valid());
        });
        buf.writeCollection(p.added(), PreviewPayload::writeEffect);
        buf.writeCollection(p.removed(), PreviewPayload::writeEffect);
        buf.writeOptional(p.preset(), FriendlyByteBuf::writeResourceLocation);
    }

    private static PreviewPayload read(FriendlyByteBuf buf) {
        return new PreviewPayload(
                buf.readList(b -> new PolicyEditor.Entry(b.readUtf(), b.readUtf(), b.readOptional(FriendlyByteBuf::readResourceLocation),
                        b.readResourceLocation(), b.readBoolean())),
                buf.readList(PreviewPayload::readEffect),
                buf.readList(PreviewPayload::readEffect),
                buf.readOptional(FriendlyByteBuf::readResourceLocation));
    }

    private static void writeEffect(FriendlyByteBuf buf, PackContent.Effect e) {
        buf.writeUtf(e.kind());
        buf.writeResourceLocation(e.target());
        buf.writeResourceLocation(e.item());
    }

    private static PackContent.Effect readEffect(FriendlyByteBuf buf) {
        return new PackContent.Effect(buf.readUtf(), buf.readResourceLocation(), buf.readResourceLocation());
    }
}
