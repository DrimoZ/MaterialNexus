package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.RecipeFamilies;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: the recipes producing one material form, with their status (MNX-009). */
public record RecipeFamilyPayload(String material, String form, List<RecipeFamilies.Row> rows) implements CustomPacketPayload {
    public static final Type<RecipeFamilyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "recipe_family"));
    public static final StreamCodec<FriendlyByteBuf, RecipeFamilyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.material());
                buf.writeUtf(p.form());
                buf.writeCollection(p.rows(), (b, r) -> {
                    b.writeResourceLocation(r.recipe());
                    b.writeResourceLocation(r.type());
                    b.writeResourceLocation(r.output());
                    b.writeEnum(r.status());
                });
            },
            buf -> new RecipeFamilyPayload(buf.readUtf(), buf.readUtf(), buf.readList(b -> new RecipeFamilies.Row(
                    b.readResourceLocation(), b.readResourceLocation(), b.readResourceLocation(), b.readEnum(RecipeFamilies.Status.class)))));

    public RecipeFamilyPayload {
        rows = List.copyOf(rows);
    }

    @Override public Type<RecipeFamilyPayload> type() { return TYPE; }
}
