package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the recipe family of one material form (MNX-009). */
public record RecipeFamilyRequest(String material, String form) implements CustomPacketPayload {
    public static final Type<RecipeFamilyRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "recipe_family_request"));
    public static final StreamCodec<FriendlyByteBuf, RecipeFamilyRequest> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> { buf.writeUtf(r.material(), NexusQueries.MAX_QUERY); buf.writeUtf(r.form(), NexusQueries.MAX_QUERY); },
            buf -> new RecipeFamilyRequest(buf.readUtf(NexusQueries.MAX_QUERY), buf.readUtf(NexusQueries.MAX_QUERY)));

    @Override public Type<RecipeFamilyRequest> type() { return TYPE; }
}
