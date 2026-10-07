package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: materials × forms status grid, row-major ({@link NexusQueries#matrix}). */
public record MatrixPayload(List<String> materials, List<String> forms, byte[] cells) implements CustomPacketPayload {
    public static final Type<MatrixPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "matrix"));
    public static final StreamCodec<FriendlyByteBuf, MatrixPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeCollection(p.materials(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(p.forms(), FriendlyByteBuf::writeUtf);
                buf.writeByteArray(p.cells());
            },
            buf -> new MatrixPayload(buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readUtf), buf.readByteArray()));

    public MatrixPayload {
        materials = List.copyOf(materials);
        forms = List.copyOf(forms);
    }

    public int cell(int material, int form) { return cells[material * forms.size() + form]; }

    @Override public Type<MatrixPayload> type() { return TYPE; }
}
