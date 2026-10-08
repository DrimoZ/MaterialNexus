package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Server to client: what scripts decide (MNX-076), each against what Material Nexus keeps now, plus the number of
 * recipes scripts created. Sent once per {@link ScriptsRequest}; not a state stream.
 */
public record ScriptsPayload(List<Row> rows, int addedRecipes) implements CustomPacketPayload {
    /** Evidence lines sent per row; the report has them all. */
    static final int MAX_EVIDENCE = 6;

    public record Row(String material, String form, Optional<ResourceLocation> kept, Optional<ResourceLocation> current,
                      ScriptDecisions.Status status, List<ScriptDecisions.Evidence> evidence, int evidenceCount, List<String> seenIn) { }

    public static final Type<ScriptsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "scripts"));
    public static final StreamCodec<FriendlyByteBuf, ScriptsPayload> STREAM_CODEC = StreamCodec.of(ScriptsPayload::write, ScriptsPayload::read);

    public ScriptsPayload {
        rows = List.copyOf(rows);
    }

    public static ScriptsPayload of(ResolvedSnapshot snapshot) {
        List<Row> rows = snapshot.scriptDecisions().stream().map(d -> {
            Optional<ResolvedForm> current = snapshot.form(d.key());
            return new Row(d.key().material().name(), d.key().form().name(), d.kept(), current.flatMap(ResolvedForm::canonical),
                    ScriptDecisions.status(d, current), d.evidence().stream().limit(MAX_EVIDENCE).toList(), d.evidence().size(), d.seenIn());
        }).toList();
        int added = (int) snapshot.scripts().recipes().stream().filter(r -> r.kind() == ScriptChanges.Kind.ADDED).count();
        return new ScriptsPayload(rows, added);
    }

    @Override public Type<ScriptsPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, ScriptsPayload p) {
        buf.writeCollection(p.rows(), (b, r) -> {
            b.writeUtf(r.material());
            b.writeUtf(r.form());
            b.writeOptional(r.kept(), FriendlyByteBuf::writeResourceLocation);
            b.writeOptional(r.current(), FriendlyByteBuf::writeResourceLocation);
            b.writeEnum(r.status());
            b.writeCollection(r.evidence(), (e, ev) -> {
                e.writeUtf(ev.kind());
                e.writeResourceLocation(ev.where());
                e.writeResourceLocation(ev.from());
                e.writeOptional(ev.to(), FriendlyByteBuf::writeResourceLocation);
            });
            b.writeVarInt(r.evidenceCount());
            b.writeCollection(r.seenIn(), FriendlyByteBuf::writeUtf);
        });
        buf.writeVarInt(p.addedRecipes());
    }

    private static ScriptsPayload read(FriendlyByteBuf buf) {
        List<Row> rows = buf.readList(b -> new Row(b.readUtf(), b.readUtf(),
                b.readOptional(FriendlyByteBuf::readResourceLocation), b.readOptional(FriendlyByteBuf::readResourceLocation),
                b.readEnum(ScriptDecisions.Status.class),
                b.readList(e -> new ScriptDecisions.Evidence(e.readUtf(), e.readResourceLocation(), e.readResourceLocation(),
                        e.readOptional(FriendlyByteBuf::readResourceLocation))),
                b.readVarInt(), b.readList(FriendlyByteBuf::readUtf)));
        return new ScriptsPayload(rows, buf.readVarInt());
    }
}
