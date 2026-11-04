package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server to client, once per data sync (login and each reload): the alternatives of the applied
 * unification, so recipe viewers can hide them (ADR-004: viewers consume gameplay truth), and what each alternative
 * becomes, for item tooltips (MNX-054). Not periodic.
 */
public record UnifiedItemsPayload(List<Identifier> alternatives, java.util.Map<Identifier, Identifier> becomes) implements CustomPacketPayload {
    public static final Type<UnifiedItemsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MaterialNexus.MOD_ID, "unified_items"));
    public static final StreamCodec<FriendlyByteBuf, UnifiedItemsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeCollection(p.alternatives(), FriendlyByteBuf::writeIdentifier);
                buf.writeMap(p.becomes(), FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeIdentifier);
            },
            buf -> new UnifiedItemsPayload(buf.readList(FriendlyByteBuf::readIdentifier),
                    buf.readMap(FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readIdentifier)));

    public UnifiedItemsPayload {
        alternatives = List.copyOf(alternatives);
        becomes = java.util.Map.copyOf(becomes);
    }

    @Override public Type<UnifiedItemsPayload> type() { return TYPE; }
}
