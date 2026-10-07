package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.client.ClientHooks;
import dev.drimoz.materialnexus.command.MaterialsCommand;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Request/response only: the client asks for one view, the server answers once (docs/09).
 * Client handler bodies call {@link ClientHooks}, so client classes never load on a dedicated server.
 */
public final class MnxNetwork {
    private static final String VERSION = "11";
    /** Both sides must run the same protocol version: a client without the mod, or another version, is refused. */
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(dev.drimoz.materialnexus.MaterialNexus.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static int nextId;

    private MnxNetwork() { }

    /** Called once from the mod constructor. Message ids follow registration order, identical on both sides. */
    public static void register() {
        toClient(OpenNexusPayload.class, OpenNexusPayload.STREAM_CODEC, p -> ClientHooks.openNexus(p));
        toClient(MaterialListPayload.class, MaterialListPayload.STREAM_CODEC, p -> ClientHooks.onMaterialList(p));
        toClient(MaterialDetailPayload.class, MaterialDetailPayload.STREAM_CODEC, p -> ClientHooks.onMaterialDetail(p));
        toClient(PreviewPayload.class, PreviewPayload.STREAM_CODEC, p -> ClientHooks.onPreview(p));
        toClient(UnifiedItemsPayload.class, UnifiedItemsPayload.STREAM_CODEC, p -> ClientHooks.onUnifiedItems(p));
        toClient(RecipeFamilyPayload.class, RecipeFamilyPayload.STREAM_CODEC, p -> ClientHooks.onRecipeFamily(p));
        toClient(ProcessPayload.class, ProcessPayload.STREAM_CODEC, p -> ClientHooks.onProcess(p));
        toClient(DataListPayload.class, DataListPayload.STREAM_CODEC, p -> ClientHooks.onDataList(p));
        toClient(DataReadPayload.class, DataReadPayload.STREAM_CODEC, p -> ClientHooks.onDataRead(p));
        toClient(MatrixPayload.class, MatrixPayload.STREAM_CODEC, p -> ClientHooks.onMatrix(p));
        toClient(SuggestionsPayload.class, SuggestionsPayload.STREAM_CODEC, p -> ClientHooks.onSuggestions(p));

        toServer(MaterialListRequest.class, MaterialListRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, NexusQueries.listPage(SnapshotManager.current(), req.page(), req.query(), req.byForm(), req.status()));
        });
        toServer(MaterialDetailRequest.class, MaterialDetailRequest.STREAM_CODEC, (req, ctx) -> {
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
        toServer(PreviewRequest.class, PreviewRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.preview(player, req);
        });
        toServer(RevertRequest.class, RevertRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.revert(player);
        });
        toServer(RestoreRequest.class, RestoreRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.restore(player, req.snapshot());
        });
        toServer(RecipeFamilyRequest.class, RecipeFamilyRequest.STREAM_CODEC, (req, ctx) -> {
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
        toServer(ProcessRequest.class, ProcessRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PolicyHandler.process(player, req.form());
        });
        toServer(DataListRequest.class, DataListRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player == null) return;
            var resources = player.server.getResourceManager();
            var entries = dev.drimoz.materialnexus.datapack.EditableData.list(resources).stream()
                    .map(e -> new DataListPayload.Item(e.kind().key, e.id(), e.source(), e.edited())).toList();
            var forms = SnapshotManager.current().discovered().materials().values().stream()
                    .flatMap(m -> m.keySet().stream()).map(f -> f.name()).distinct().sorted().toList();
            PacketDistributor.sendToPlayer(player, new DataListPayload(entries, MaterialsCommand.untagged(SnapshotManager.current()), forms));
        });
        toServer(DataReadRequest.class, DataReadRequest.STREAM_CODEC, (req, ctx) -> {
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
        toServer(MatrixRequest.class, MatrixRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, NexusQueries.matrix(SnapshotManager.current()));
        });
        toServer(SuggestionsRequest.class, SuggestionsRequest.STREAM_CODEC, (req, ctx) -> {
            ServerPlayer player = authorized(ctx);
            if (player != null) PacketDistributor.sendToPlayer(player, req.saved() ? SuggestionsPayload.saved(PolicyHandler.savedChoices())
                    : SuggestionsPayload.of(SnapshotManager.current()));
        });
    }

    private static <T extends CustomPacketPayload> void toClient(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, Consumer<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> codec.encode(buf, msg)).decoder(codec::decode)
                .consumerMainThread((msg, ctx) -> handler.accept(msg)).add();
    }

    /** Server-bound handlers run on the server thread, like the NeoForge payload handlers on main. */
    private static <T extends CustomPacketPayload> void toServer(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, NetworkEvent.Context> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> codec.encode(buf, msg)).decoder(codec::decode)
                .consumerMainThread((msg, ctx) -> handler.accept(msg, ctx.get())).add();
    }

    /** Permission is re-checked on every request; opening the screen once grants nothing. */
    private static ServerPlayer authorized(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        return player != null && player.hasPermissions(MaterialsCommand.PERMISSION_LEVEL) ? player : null;
    }
}
