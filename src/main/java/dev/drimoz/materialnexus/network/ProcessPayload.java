package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Server to client: the form's rule as written in global.json, and existing routes to pick from (MNX-036). */
public record ProcessPayload(String form, Optional<ProcessRules.Rule> rule, ProcessRules.Rule examples) implements CustomPacketPayload {
    public static final Type<ProcessPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "process"));
    public static final StreamCodec<FriendlyByteBuf, ProcessPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.form(), NexusQueries.MAX_QUERY);
                buf.writeOptional(p.rule(), PreviewRequest::writeRule);
                PreviewRequest.writeRule(buf, p.examples());
            },
            buf -> new ProcessPayload(buf.readUtf(NexusQueries.MAX_QUERY), buf.readOptional(PreviewRequest::readRule), PreviewRequest.readRule(buf)));

    @Override public Type<ProcessPayload> type() { return TYPE; }
}
