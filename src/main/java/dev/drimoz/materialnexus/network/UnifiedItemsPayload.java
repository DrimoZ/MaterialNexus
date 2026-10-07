package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client, once per data sync (login and each reload): the alternatives of the applied
 * unification, so recipe viewers can hide them (ADR-004: viewers consume gameplay truth), and what each alternative
 * becomes, for item tooltips (MNX-054). Not periodic.
 */
public record UnifiedItemsPayload(List<ResourceLocation> alternatives, java.util.Map<ResourceLocation, ResourceLocation> becomes) implements CustomPacketPayload {
    public static final Type<UnifiedItemsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "unified_items"));
    public static final StreamCodec<FriendlyByteBuf, UnifiedItemsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeCollection(p.alternatives(), FriendlyByteBuf::writeResourceLocation);
                buf.writeMap(p.becomes(), FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeResourceLocation);
            },
            buf -> new UnifiedItemsPayload(buf.readList(FriendlyByteBuf::readResourceLocation),
                    buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readResourceLocation)));

    public UnifiedItemsPayload {
        alternatives = List.copyOf(alternatives);
        becomes = java.util.Map.copyOf(becomes);
    }

    @Override public Type<UnifiedItemsPayload> type() { return TYPE; }
}
