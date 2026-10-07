package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.EditableData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Server to client: one editable data file as the game uses it, empty when it does not exist yet (MNX-046). */
public record DataReadPayload(String kind, ResourceLocation id, Optional<String> text, boolean edited) implements CustomPacketPayload {
    public static final Type<DataReadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "data_read"));
    public static final StreamCodec<FriendlyByteBuf, DataReadPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.kind());
                buf.writeResourceLocation(p.id());
                buf.writeOptional(p.text(), (b, t) -> b.writeUtf(t, EditableData.MAX_TEXT));
                buf.writeBoolean(p.edited());
            },
            buf -> new DataReadPayload(buf.readUtf(), buf.readResourceLocation(), buf.readOptional(b -> b.readUtf(EditableData.MAX_TEXT)), buf.readBoolean()));

    @Override public Type<DataReadPayload> type() { return TYPE; }
}
