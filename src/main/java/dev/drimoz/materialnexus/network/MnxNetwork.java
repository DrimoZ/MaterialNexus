package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.client.ClientHooks;
import dev.drimoz.materialnexus.command.MaterialsCommand;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Request/response only: the client asks for one view, the server answers once (docs/09).
 * Client handler bodies call {@link ClientHooks}, so client classes never load on a dedicated server.
 */
public final class MnxNetwork {
    private static final String VERSION = "1";

    private MnxNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(OpenNexusPayload.TYPE, OpenNexusPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.openNexus(p));
        registrar.playToClient(MaterialListPayload.TYPE, MaterialListPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onMaterialList(p));
        registrar.playToClient(MaterialDetailPayload.TYPE, MaterialDetailPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onMaterialDetail(p));
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onPreview(p));
        registrar.playToClient(UnifiedItemsPayload.TYPE, UnifiedItemsPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onUnifiedItems(p));
        registrar.playToClient(RecipeFamilyPayload.TYPE, RecipeFamilyPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onRecipeFamily(p));
        registrar.playToClient(SuggestionsPayload.TYPE, SuggestionsPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onSuggestions(p));

        registrar.playToServer(MaterialListRequest.TYPE, MaterialListRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, NexusQueries.listPage(SnapshotManager.current(), req.page(), req.query()));
        });
        registrar.playToServer(MaterialDetailRequest.TYPE, MaterialDetailRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) {
                var snapshot = SnapshotManager.current();
                NexusQueries.detail(snapshot, req.material()).ifPresent(d -> PacketDistributor.sendToPlayer(player,
                        d.withMissing(dev.drimoz.materialnexus.datapack.RecipeEdges.missing(player.server, snapshot.discovered(),
                                new dev.drimoz.materialnexus.core.domain.MaterialId(d.material())))));
            }
        });
        registrar.playToServer(PreviewRequest.TYPE, PreviewRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.preview(player, req);
        });
        registrar.playToServer(RevertRequest.TYPE, RevertRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.revert(player);
        });
        registrar.playToServer(RecipeFamilyRequest.TYPE, RecipeFamilyRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player == null) return;
            var material = dev.drimoz.materialnexus.core.domain.MaterialId.read(req.material()).result();
            var form = dev.drimoz.materialnexus.core.domain.FormId.read(req.form()).result();
            if (material.isEmpty() || form.isEmpty()) return;
            var resolved = SnapshotManager.current().materials().get(material.get());
            var f = resolved == null ? null : resolved.forms().get(form.get());
            if (f != null) PacketDistributor.sendToPlayer(player, new RecipeFamilyPayload(req.material(), req.form(),
                    dev.drimoz.materialnexus.datapack.RecipeFamilies.family(player.server, f)));
        });
        registrar.playToServer(SuggestionsRequest.TYPE, SuggestionsRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, SuggestionsPayload.of(SnapshotManager.current()));
        });
    }

    /** Permission is re-checked on every request; opening the screen once grants nothing. */
    private static ServerPlayer authorized(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer player && player.hasPermissions(MaterialsCommand.PERMISSION_LEVEL) ? player : null;
    }
}
