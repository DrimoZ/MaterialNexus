package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client, in response to {@link MaterialDetailRequest}: every resolved form of one material, or with
 * {@code byForm} one form across every material (MNX-035). {@code material} is the name of whichever was asked.
 */
public record MaterialDetailPayload(String material, boolean byForm, List<FormView> forms, List<FamilyRelations.Relation> missing,
                                    java.util.Map<ResourceLocation, dev.drimoz.materialnexus.datapack.RecipeEdges.Usage> usage)
        implements CustomPacketPayload {
    public static final Type<MaterialDetailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_detail"));
    public static final StreamCodec<FriendlyByteBuf, MaterialDetailPayload> STREAM_CODEC =
            StreamCodec.of(MaterialDetailPayload::write, MaterialDetailPayload::read);

    public record FormView(String material, String form, ResolvedForm resolved) { }

    public MaterialDetailPayload {
        forms = List.copyOf(forms);
        missing = List.copyOf(missing);
        usage = java.util.Map.copyOf(usage);
    }

    /** The same detail with the missing-recipe proposals computed on the server (MNX-010). */
    public MaterialDetailPayload withMissing(List<FamilyRelations.Relation> relations) {
        return new MaterialDetailPayload(material, byForm, forms, relations, usage);
    }

    /** The same detail with each item's recipe counts (MNX-052). */
    public MaterialDetailPayload withUsage(java.util.Map<ResourceLocation, dev.drimoz.materialnexus.datapack.RecipeEdges.Usage> counts) {
        return new MaterialDetailPayload(material, byForm, forms, missing, counts);
    }

    @Override public Type<MaterialDetailPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, MaterialDetailPayload p) {
        buf.writeUtf(p.material());
        buf.writeBoolean(p.byForm());
        buf.writeCollection(p.forms(), (b, view) -> {
            ResolvedForm f = view.resolved();
            b.writeUtf(view.material());
            b.writeUtf(view.form());
            b.writeOptional(f.canonical(), FriendlyByteBuf::writeResourceLocation);
            b.writeCollection(f.alternatives(), FriendlyByteBuf::writeResourceLocation);
            b.writeCollection(f.notUnified(), (bb, n) -> {
                bb.writeResourceLocation(n.item());
                bb.writeUtf(n.reasonKey());
                bb.writeCollection(n.reasonArgs(), FriendlyByteBuf::writeUtf);
            });
            b.writeEnum(f.source());
            b.writeEnum(f.confidence());
            b.writeUtf(f.reasonKey());
            b.writeCollection(f.reasonArgs(), FriendlyByteBuf::writeUtf);
            b.writeOptional(f.ignoredOverride(), FriendlyByteBuf::writeResourceLocation);
        });
        buf.writeCollection(p.missing(), (b, r) -> {
            b.writeUtf(r.from().name());
            b.writeUtf(r.to().name());
        });
        buf.writeMap(p.usage(), FriendlyByteBuf::writeResourceLocation, (b, u) -> {
            b.writeVarInt(u.produced());
            b.writeVarInt(u.used());
        });
    }

    private static MaterialDetailPayload read(FriendlyByteBuf buf) {
        return new MaterialDetailPayload(buf.readUtf(), buf.readBoolean(), buf.readList(b -> new FormView(b.readUtf(), b.readUtf(), new ResolvedForm(
                b.readOptional(FriendlyByteBuf::readResourceLocation),
                b.readList(FriendlyByteBuf::readResourceLocation),
                b.readList(bb -> new ResolvedForm.NotUnified(bb.readResourceLocation(), bb.readUtf(), bb.readList(FriendlyByteBuf::readUtf))),
                b.readEnum(PolicyPrecedence.class),
                b.readEnum(Confidence.class),
                b.readUtf(),
                b.readList(FriendlyByteBuf::readUtf),
                b.readOptional(FriendlyByteBuf::readResourceLocation)))),
                buf.readList(b -> new FamilyRelations.Relation(new FormId(b.readUtf()), new FormId(b.readUtf()))),
                buf.readMap(FriendlyByteBuf::readResourceLocation, b -> new dev.drimoz.materialnexus.datapack.RecipeEdges.Usage(b.readVarInt(), b.readVarInt())));
    }
}
