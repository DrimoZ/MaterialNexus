package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: every resolved form of one material, in response to {@link MaterialDetailRequest}. */
public record MaterialDetailPayload(String material, List<FormView> forms) implements CustomPacketPayload {
    public static final Type<MaterialDetailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material_detail"));
    public static final StreamCodec<FriendlyByteBuf, MaterialDetailPayload> STREAM_CODEC =
            StreamCodec.of(MaterialDetailPayload::write, MaterialDetailPayload::read);

    public record FormView(String form, ResolvedForm resolved) { }

    public MaterialDetailPayload {
        forms = List.copyOf(forms);
    }

    @Override public Type<MaterialDetailPayload> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, MaterialDetailPayload p) {
        buf.writeUtf(p.material());
        buf.writeCollection(p.forms(), (b, view) -> {
            ResolvedForm f = view.resolved();
            b.writeUtf(view.form());
            b.writeResourceLocation(f.canonical());
            b.writeCollection(f.alternatives(), FriendlyByteBuf::writeResourceLocation);
            b.writeEnum(f.source());
            b.writeEnum(f.confidence());
            b.writeUtf(f.reasonKey());
            b.writeCollection(f.reasonArgs(), FriendlyByteBuf::writeUtf);
            b.writeOptional(f.ignoredOverride(), FriendlyByteBuf::writeResourceLocation);
        });
    }

    private static MaterialDetailPayload read(FriendlyByteBuf buf) {
        return new MaterialDetailPayload(buf.readUtf(), buf.readList(b -> new FormView(b.readUtf(), new ResolvedForm(
                b.readResourceLocation(),
                b.readList(FriendlyByteBuf::readResourceLocation),
                b.readEnum(PolicyPrecedence.class),
                b.readEnum(Confidence.class),
                b.readUtf(),
                b.readList(FriendlyByteBuf::readUtf),
                b.readOptional(FriendlyByteBuf::readResourceLocation)))));
    }
}
