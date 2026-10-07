package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.FormPatterns;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client (MNX-046): every editable data file with the pack providing it, the untagged-form audit, and the
 * forms a pattern can be declared for.
 */
public record DataListPayload(List<Item> entries, List<FormPatterns.Candidate> untagged, List<String> forms) implements CustomPacketPayload {
    public record Item(String kind, ResourceLocation id, String source, boolean edited) { }

    public static final Type<DataListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "data_list"));
    public static final StreamCodec<FriendlyByteBuf, DataListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeCollection(p.entries(), (b, e) -> {
                    b.writeUtf(e.kind());
                    b.writeResourceLocation(e.id());
                    b.writeUtf(e.source());
                    b.writeBoolean(e.edited());
                });
                buf.writeCollection(p.untagged(), (b, c) -> {
                    b.writeUtf(c.pattern());
                    b.writeCollection(c.materials(), FriendlyByteBuf::writeUtf);
                });
                buf.writeCollection(p.forms(), FriendlyByteBuf::writeUtf);
            },
            buf -> new DataListPayload(
                    buf.readList(b -> new Item(b.readUtf(), b.readResourceLocation(), b.readUtf(), b.readBoolean())),
                    buf.readList(b -> new FormPatterns.Candidate(b.readUtf(), b.readList(FriendlyByteBuf::readUtf))),
                    buf.readList(FriendlyByteBuf::readUtf)));

    public DataListPayload {
        entries = List.copyOf(entries);
        untagged = List.copyOf(untagged);
        forms = List.copyOf(forms);
    }

    @Override public Type<DataListPayload> type() { return TYPE; }
}
