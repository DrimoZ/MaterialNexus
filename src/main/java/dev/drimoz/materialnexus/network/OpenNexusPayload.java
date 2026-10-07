package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client, sent once when an authorized player opens Material Nexus, or after an apply /
 * revert was written ({@code applied}: the client may then drop its pending changes). Not a state stream.
 */
public record OpenNexusPayload(boolean readOnly, boolean canRevert, boolean applied, java.util.Map<ResourceLocation, String> presets, String global,
                               String history)
        implements CustomPacketPayload {
    public static final Type<OpenNexusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "open_nexus"));
    private static final int MAX_TEXT = dev.drimoz.materialnexus.datapack.EditableData.MAX_TEXT;
    public static final StreamCodec<FriendlyByteBuf, OpenNexusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.readOnly());
                buf.writeBoolean(p.canRevert());
                buf.writeBoolean(p.applied());
                buf.writeMap(p.presets(), FriendlyByteBuf::writeResourceLocation, (b, text) -> b.writeUtf(text, MAX_TEXT));
                buf.writeUtf(p.global(), MAX_TEXT);
                buf.writeUtf(p.history(), MAX_TEXT);
            },
            buf -> new OpenNexusPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readMap(java.util.HashMap::new, FriendlyByteBuf::readResourceLocation, b -> b.readUtf(MAX_TEXT)),
                    buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)));

    public static OpenNexusPayload forPlayer(ServerPlayer player) {
        return forPlayer(player, false);
    }

    /** Editing is a singleplayer/LAN activity; dedicated servers only get read-only diagnostics (ADR-013). */
    public static OpenNexusPayload forPlayer(ServerPlayer player, boolean applied) {
        boolean readOnly = player.server.isDedicatedServer();
        return new OpenNexusPayload(readOnly, !readOnly && PolicyEditor.canRevert(MnxPaths.policies()), applied,
                presetTexts(), currentGlobal(),
                dev.drimoz.materialnexus.datapack.ApplyHistory.latest());
    }

    /** Every preset with its JSON (MNX-057: the Presets view shows what each one sets). */
    private static java.util.Map<ResourceLocation, String> presetTexts() {
        java.util.Map<ResourceLocation, String> out = new java.util.TreeMap<>();
        dev.drimoz.materialnexus.datapack.Presets.all().forEach((id, json) -> out.put(id, json.toString()));
        return out;
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
