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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /**
     * Forge 1.20.1: vanilla refuses a custom payload over 32767 bytes to the server (even in singleplayer) and over 1 MiB to
     * the client, and Forge does not split channel messages as NeoForge does on main. A larger message travels as ordered
     * {@link Part}s, decoded once whole.
     */
    private static final int TO_SERVER_PART = 32_000;
    private static final int TO_CLIENT_PART = 1_000_000;
    /** Bound of one reassembled message to the server: 64 edits of 64 Ki characters (3 bytes each) and the changes. */
    private static final int MAX_TO_SERVER = 16 * 1024 * 1024;
    private static final int MAX_TO_CLIENT = 64 * 1024 * 1024;

    private record Registered<T>(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
                                 BiConsumer<T, NetworkEvent.Context> handler, boolean toServer) { }

    private static final List<Registered<?>> REGISTERED = new ArrayList<>();
    private static final Map<Class<?>, Integer> INDEX = new HashMap<>();

    /** One slice of a large message: {@code message} is its index in {@link #REGISTERED}. */
    private record Part(int message, int index, int count, byte[] bytes) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(message);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
            buf.writeByteArray(bytes);
        }

        static Part read(FriendlyByteBuf buf) {
            return new Part(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(TO_CLIENT_PART));
        }
    }

    private static final class Assembly {
        final int message;
        final int count;
        int next;
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

        Assembly(int message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    /** Server thread only: one message being received per player. */
    private static final Map<UUID, Assembly> FROM_PLAYERS = new HashMap<>();
    /** Client thread only. */
    private static Assembly fromServer;

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
        registerParts();
    }

    private static <T extends CustomPacketPayload> void toClient(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, Consumer<T> handler) {
        add(new Registered<>(type, codec, (msg, ctx) -> handler.accept(msg), false), NetworkDirection.PLAY_TO_CLIENT);
    }

    /** Server-bound handlers run on the server thread, like the NeoForge payload handlers on main. */
    private static <T extends CustomPacketPayload> void toServer(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, NetworkEvent.Context> handler) {
        add(new Registered<>(type, codec, handler, true), NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T> void add(Registered<T> registered, NetworkDirection direction) {
        INDEX.put(registered.type(), REGISTERED.size());
        REGISTERED.add(registered);
        CHANNEL.messageBuilder(registered.type(), nextId++, direction)
                .encoder((msg, buf) -> registered.codec().encode(buf, msg)).decoder(registered.codec()::decode)
                .consumerMainThread((msg, ctx) -> registered.handler().accept(msg, ctx.get())).add();
    }

    /** Registered last, in both directions. */
    private static void registerParts() {
        CHANNEL.messageBuilder(Part.class, nextId++).encoder(Part::write).decoder(Part::read)
                .consumerMainThread((part, ctx) -> receive(part, ctx.get())).add();
    }

    static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        send(payload, TO_CLIENT_PART, msg -> CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), msg));
    }

    static void sendToServer(CustomPacketPayload payload) {
        send(payload, TO_SERVER_PART, CHANNEL::sendToServer);
    }

    // ponytail: encodes every message once to measure it, then the channel encodes it again; split only when needed.
    private static void send(CustomPacketPayload payload, int limit, Consumer<Object> out) {
        int message = INDEX.get(payload.getClass());
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            encode(REGISTERED.get(message), payload, buf);
            if (buf.readableBytes() <= limit) {
                out.accept(payload);
                return;
            }
            byte[] all = new byte[buf.readableBytes()];
            buf.readBytes(all);
            int count = (all.length + limit - 1) / limit;
            for (int i = 0; i < count; i++) {
                out.accept(new Part(message, i, count, Arrays.copyOfRange(all, i * limit, Math.min(all.length, (i + 1) * limit))));
            }
        } finally {
            buf.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void encode(Registered<T> registered, Object payload, FriendlyByteBuf buf) {
        registered.codec().encode(buf, (T) payload);
    }

    /** Untrusted on the server: a part out of order, for an unknown or client-bound message, or past the bound drops it all. */
    private static void receive(Part part, NetworkEvent.Context ctx) {
        boolean server = ctx.getDirection().getReceptionSide().isServer();
        ServerPlayer sender = ctx.getSender();
        UUID key = sender == null ? null : sender.getUUID();
        Registered<?> registered = part.message() >= 0 && part.message() < REGISTERED.size() ? REGISTERED.get(part.message()) : null;
        int max = server ? MAX_TO_SERVER : MAX_TO_CLIENT;
        int partSize = server ? TO_SERVER_PART : TO_CLIENT_PART;
        Assembly assembly = server ? FROM_PLAYERS.remove(key) : fromServer;
        if (!server) fromServer = null;
        if (registered == null || registered.toServer() != server || (server && key == null) || part.count() < 2
                || part.count() > max / partSize + 1 || part.bytes().length > partSize) return;
        if (part.index() == 0) {
            // Nothing is buffered for a player who may not use Material Nexus.
            if (server && authorized(ctx) == null) return;
            assembly = new Assembly(part.message(), part.count());
        } else if (assembly == null || assembly.message != part.message() || assembly.count != part.count() || assembly.next != part.index()) {
            return;
        }
        assembly.bytes.writeBytes(part.bytes());
        assembly.next++;
        if (assembly.next < assembly.count) {
            if (server) FROM_PLAYERS.put(key, assembly);
            else fromServer = assembly;
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(assembly.bytes.toByteArray()));
        try {
            dispatch(registered, buf, ctx);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Dropped an invalid Material Nexus message ({})", registered.type().getSimpleName(), e);
        }
    }

    private static <T> void dispatch(Registered<T> registered, FriendlyByteBuf buf, NetworkEvent.Context ctx) {
        registered.handler().accept(registered.codec().decode(buf), ctx);
    }

    /** Permission is re-checked on every request; opening the screen once grants nothing. */
    private static ServerPlayer authorized(NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        return player != null && player.hasPermissions(MaterialsCommand.PERMISSION_LEVEL) ? player : null;
    }
}
