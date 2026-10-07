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
    private static final String VERSION = "11";

    private MnxNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(OpenNexusPayload.TYPE, OpenNexusPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.openNexus(p));
        registrar.playToClient(MaterialListPayload.TYPE, MaterialListPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onMaterialList(p));
        registrar.playToClient(MaterialDetailPayload.TYPE, MaterialDetailPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onMaterialDetail(p));
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onPreview(p));
        registrar.playToClient(UnifiedItemsPayload.TYPE, UnifiedItemsPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onUnifiedItems(p));
        registrar.playToClient(RecipeFamilyPayload.TYPE, RecipeFamilyPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onRecipeFamily(p));
        registrar.playToClient(ProcessPayload.TYPE, ProcessPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onProcess(p));
        registrar.playToClient(DataListPayload.TYPE, DataListPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onDataList(p));
        registrar.playToClient(DataReadPayload.TYPE, DataReadPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onDataRead(p));
        registrar.playToClient(MatrixPayload.TYPE, MatrixPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onMatrix(p));
        registrar.playToClient(SuggestionsPayload.TYPE, SuggestionsPayload.STREAM_CODEC, (p, ctx) -> ClientHooks.onSuggestions(p));

        registrar.playToServer(MaterialListRequest.TYPE, MaterialListRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, NexusQueries.listPage(SnapshotManager.current(), req.page(), req.query(), req.byForm(), req.status()));
        });
        registrar.playToServer(MaterialDetailRequest.TYPE, MaterialDetailRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) {
                var snapshot = SnapshotManager.current();
                NexusQueries.detail(snapshot, req.material(), req.byForm()).ifPresent(d -> {
                    // One pass over the recipes serves both the missing conversions and the per-item counts (MNX-052).
                    var edges = dev.drimoz.materialnexus.datapack.RecipeEdges.edges(player.server);
                    java.util.Set<net.minecraft.resources.ResourceLocation> items = new java.util.HashSet<>();
                    d.forms().forEach(v -> {
                        v.resolved().canonical().ifPresent(items::add);
                        items.addAll(v.resolved().alternatives());
                        v.resolved().notUnified().forEach(n -> items.add(n.item()));
                    });
                    var withUsage = d.withUsage(dev.drimoz.materialnexus.datapack.RecipeEdges.usage(edges, items));
                    PacketDistributor.sendToPlayer(player, d.byForm() ? withUsage : withUsage.withMissing(
                            dev.drimoz.materialnexus.datapack.RecipeEdges.missing(edges, snapshot.discovered(),
                                    new dev.drimoz.materialnexus.core.domain.MaterialId(d.material()))));
                });
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
        registrar.playToServer(RestoreRequest.TYPE, RestoreRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.restore(player, req.snapshot());
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
        registrar.playToServer(ProcessRequest.TYPE, ProcessRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.process(player, req.form());
        });
        registrar.playToServer(DataListRequest.TYPE, DataListRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player == null) return;
            var resources = player.server.getResourceManager();
            var entries = dev.drimoz.materialnexus.datapack.EditableData.list(resources).stream()
                    .map(e -> new DataListPayload.Item(e.kind().key, e.id(), e.source(), e.edited())).toList();
            var forms = SnapshotManager.current().discovered().materials().values().stream()
                    .flatMap(m -> m.keySet().stream()).map(f -> f.name()).distinct().sorted().toList();
            PacketDistributor.sendToPlayer(player, new DataListPayload(entries, MaterialsCommand.untagged(SnapshotManager.current()), forms));
        });
        registrar.playToServer(DataReadRequest.TYPE, DataReadRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player == null) return;
            dev.drimoz.materialnexus.datapack.EditableData.Kind.byKey(req.kind()).ifPresent(kind -> {
                var resources = player.server.getResourceManager();
                var text = dev.drimoz.materialnexus.datapack.EditableData.read(resources, kind, req.id());
                boolean edited = dev.drimoz.materialnexus.datapack.EditableData.list(resources).stream()
                        .anyMatch(e -> e.kind() == kind && e.id().equals(req.id()) && e.edited());
                PacketDistributor.sendToPlayer(player, new DataReadPayload(req.kind(), req.id(), text, edited));
            });
        });
        registrar.playToServer(MatrixRequest.TYPE, MatrixRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, NexusQueries.matrix(SnapshotManager.current()));
        });
        registrar.playToServer(SuggestionsRequest.TYPE, SuggestionsRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, req.saved() ? SuggestionsPayload.saved(PolicyHandler.savedChoices())
                    : SuggestionsPayload.of(SnapshotManager.current()));
        });
    }

    /** Permission is re-checked on every request; opening the screen once grants nothing. */
    private static ServerPlayer authorized(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer player && player.hasPermissions(MaterialsCommand.PERMISSION_LEVEL) ? player : null;
    }
}
