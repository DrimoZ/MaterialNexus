package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client, sent once when an authorized player opens Material Nexus, or after an apply /
 * revert was written ({@code applied}: the client may then drop its pending changes). Not a state stream.
 */
public record OpenNexusPayload(boolean readOnly, boolean canRevert, boolean applied, java.util.List<ResourceLocation> presets, String global,
                               String history)
        implements CustomPacketPayload {
    public static final Type<OpenNexusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "open_nexus"));
    public static final StreamCodec<ByteBuf, OpenNexusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenNexusPayload::readOnly,
            ByteBufCodecs.BOOL, OpenNexusPayload::canRevert,
            ByteBufCodecs.BOOL, OpenNexusPayload::applied,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenNexusPayload::presets,
            ByteBufCodecs.stringUtf8(dev.drimoz.materialnexus.datapack.EditableData.MAX_TEXT), OpenNexusPayload::global,
            ByteBufCodecs.stringUtf8(dev.drimoz.materialnexus.datapack.EditableData.MAX_TEXT), OpenNexusPayload::history,
            OpenNexusPayload::new);

    public static OpenNexusPayload forPlayer(ServerPlayer player) {
        return forPlayer(player, false);
    }

    /** Editing is a singleplayer/LAN activity; dedicated servers only get read-only diagnostics (ADR-013). */
    public static OpenNexusPayload forPlayer(ServerPlayer player, boolean applied) {
        boolean readOnly = player.server.isDedicatedServer();
        return new OpenNexusPayload(readOnly, !readOnly && PolicyEditor.canRevert(MnxPaths.policies()), applied,
                java.util.List.copyOf(dev.drimoz.materialnexus.datapack.Presets.all().keySet()), currentGlobal(),
                dev.drimoz.materialnexus.datapack.ApplyHistory.latest());
    }

    /** global.json as written, for the GUI to edit some of its fields (MNX-050); "{}" when there is none or it is unreadable. */
    private static String currentGlobal() {
        java.nio.file.Path file = MnxPaths.policies().resolve(dev.drimoz.materialnexus.datapack.PolicyFiles.GLOBAL_FILE);
        try {
            return java.nio.file.Files.isRegularFile(file) ? com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().toString() : "{}";
        } catch (java.io.IOException | RuntimeException e) {
            return "{}";
        }
    }

    @Override public Type<OpenNexusPayload> type() { return TYPE; }
}
