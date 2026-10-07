package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: one page of material summaries, in response to {@link MaterialListRequest}. */
public record MaterialListPayload(int page, int pageCount, int totalMatches, String query, boolean byForm, List<Summary> entries, Totals totals)
        implements CustomPacketPayload {
    public static final Type<MaterialListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_list"));
    public static final StreamCodec<FriendlyByteBuf, MaterialListPayload> STREAM_CODEC =
            StreamCodec.of(MaterialListPayload::write, MaterialListPayload::read);

    /** One row: how many forms (materials, by form), items, and of those with more than one provider. */
    public record Summary(String material, int forms, int providers, int duplicateForms, int unifiedForms, java.util.Optional<ResourceLocation> icon) {
        public int toDecide() { return duplicateForms - unifiedForms; }
    }

    /**
     * Whole-pack counts for the top bar (MNX-049): forms to decide, forms unified, items set aside as not the same; and
     * the mods that provide duplicates, most involved first (MNX-051, mod priority).
     */
    /** {@code byPriority}: the unified forms decided by a mod priority, not by a choice (MNX-062). */
    public record Totals(int toDecide, int unified, int byPriority, int setAside, List<String> mods) {
        public Totals {
            mods = List.copyOf(mods);
        }
    }

    public MaterialListPayload {
        entries = List.copyOf(entries);
    }

    @Override public Type<MaterialListPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, MaterialListPayload p) {
        buf.writeVarInt(p.page());
        buf.writeVarInt(p.pageCount());
        buf.writeVarInt(p.totalMatches());
        buf.writeUtf(p.query(), NexusQueries.MAX_QUERY);
        buf.writeBoolean(p.byForm());
        buf.writeCollection(p.entries(), (b, e) -> {
            b.writeUtf(e.material());
            b.writeVarInt(e.forms());
            b.writeVarInt(e.providers());
            b.writeVarInt(e.duplicateForms());
            b.writeVarInt(e.unifiedForms());
            b.writeOptional(e.icon(), FriendlyByteBuf::writeResourceLocation);
        });
        buf.writeVarInt(p.totals().toDecide());
        buf.writeVarInt(p.totals().unified());
        buf.writeVarInt(p.totals().byPriority());
        buf.writeVarInt(p.totals().setAside());
        buf.writeCollection(p.totals().mods(), FriendlyByteBuf::writeUtf);
    }

    private static MaterialListPayload read(FriendlyByteBuf buf) {
        return new MaterialListPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(NexusQueries.MAX_QUERY), buf.readBoolean(),
                buf.readList(b -> new Summary(b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readOptional(FriendlyByteBuf::readResourceLocation))),
                new Totals(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readList(FriendlyByteBuf::readUtf)));
    }
}
