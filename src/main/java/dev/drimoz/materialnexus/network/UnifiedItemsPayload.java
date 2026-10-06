package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client, once per data sync (login and each reload): the alternatives of the applied
 * unification, so recipe viewers can hide them (ADR-004: viewers consume gameplay truth). Not periodic.
 */
public record UnifiedItemsPayload(List<ResourceLocation> alternatives) implements CustomPacketPayload {
    public static final Type<UnifiedItemsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "unified_items"));
    public static final StreamCodec<FriendlyByteBuf, UnifiedItemsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeCollection(p.alternatives(), FriendlyByteBuf::writeResourceLocation),
            buf -> new UnifiedItemsPayload(buf.readList(FriendlyByteBuf::readResourceLocation)));

    public UnifiedItemsPayload {
        alternatives = List.copyOf(alternatives);
    }

    @Override public Type<UnifiedItemsPayload> type() { return TYPE; }
}
