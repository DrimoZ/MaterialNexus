package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the suggestions to add as pending choices. Nothing is written until Preview / Apply. */
public record SuggestionsPayload(List<CanonicalChange> changes) implements CustomPacketPayload {
    public static final Type<SuggestionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "suggestions"));
    public static final StreamCodec<FriendlyByteBuf, SuggestionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeCollection(p.changes(), (b, c) -> {
                b.writeUtf(c.material());
                b.writeUtf(c.form());
                b.writeResourceLocation(c.provider());
            }),
            buf -> new SuggestionsPayload(buf.readList(b -> new CanonicalChange(b.readUtf(), b.readUtf(), b.readResourceLocation()))));

    public SuggestionsPayload {
        changes = List.copyOf(changes);
    }

    @Override public Type<SuggestionsPayload> type() { return TYPE; }

    /** MNX-058: every choice written in the policy files (MNX-060: even ignored ones), as "back to default" changes. */
    public static SuggestionsPayload saved(java.util.Set<dev.drimoz.materialnexus.core.domain.MaterialForm> saved) {
        return new SuggestionsPayload(saved.stream().map(k -> new CanonicalChange(k.material().name(), k.form().name(), CanonicalChange.RESET)).toList());
    }

    public static SuggestionsPayload of(ResolvedSnapshot snapshot) {
        List<CanonicalChange> changes = new ArrayList<>();
        snapshot.materials().forEach((material, resolved) -> resolved.forms().forEach((form, f) -> {
            if (f.source() == PolicyPrecedence.DEFAULT && f.canonical().isPresent() && !f.alternatives().isEmpty()) {
                changes.add(new CanonicalChange(material.name(), form.name(), f.canonical().get()));
            }
        }));
        return new SuggestionsPayload(changes);
    }
}
