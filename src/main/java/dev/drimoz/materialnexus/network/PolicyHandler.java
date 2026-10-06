package dev.drimoz.materialnexus.network;

import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
import dev.drimoz.materialnexus.datapack.RecipeSources;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server side of Preview / Apply / Revert (ADR-009). Stateless: the client sends its pending changes each time. */
final class PolicyHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private interface PolicyWrite {
        void run() throws IOException;
    }

    private PolicyHandler() { }

    static void preview(ServerPlayer player, PreviewRequest request) {
        List<PolicyEditor.Entry> entries = PolicyEditor.preview(SnapshotManager.current(), request.changes());
        List<PackContent.Effect> proposed;
        List<PackContent.Effect> current;
        try {
            ResolutionPolicy policy = PolicyFiles.load(MnxPaths.policies()).withExplicit(explicitChoices(entries));
            current = PackContent.readManifest(MnxPaths.generated());
            proposed = packContent(player.server, policy, current).effects();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Material Nexus preview failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
            return;
        }
        List<PackContent.Effect> added = proposed.stream().filter(e -> !current.contains(e)).toList();
        List<PackContent.Effect> removed = current.stream().filter(e -> !proposed.contains(e)).toList();
        boolean valid = entries.stream().anyMatch(PolicyEditor.Entry::valid);

        if (!request.apply()) {
            PacketDistributor.sendToPlayer(player, new PreviewPayload(entries, added, removed));
            return;
        }
        if (!valid && added.isEmpty() && removed.isEmpty()) return;
        // With no valid choice, apply only regenerates the pack from the policy as written (e.g. edited by hand).
        writeAndReload(player, () -> { if (valid) PolicyEditor.apply(MnxPaths.policies(), entries); },
                Component.translatable("message.materialnexus.applied", added.size() + removed.size()));
    }

    static void revert(ServerPlayer player) {
        if (!PolicyEditor.canRevert(MnxPaths.policies())) return;
        writeAndReload(player, () -> PolicyEditor.revert(MnxPaths.policies()),
                Component.translatable("message.materialnexus.reverted"));
    }

    /** The whole generated pack for a policy: tags and conversions, then the recipes they imply. */
    private static PackContent.Content packContent(MinecraftServer server, ResolutionPolicy policy, List<PackContent.Effect> applied) {
        boolean auPresent = net.neoforged.fml.ModList.get().isLoaded(dev.drimoz.materialnexus.core.policy.AlmostUnified.MOD_ID);
        return PackContent.full(CanonicalResolver.resolve(SnapshotManager.current().discovered(), policy), policy, auPresent,
                (conversions, ownership) -> {
                    RecipeFormats formats = RecipeFormats.load(server.getResourceManager());
                    return RecipeRewrites.plan(
                            RecipeSources.collect(server, conversions, formats, RecipeRewrites.overridden(applied)), conversions, formats, ownership);
                });
    }

    private static Map<MaterialForm, ResourceLocation> explicitChoices(List<PolicyEditor.Entry> entries) {
        Map<MaterialForm, ResourceLocation> explicit = new HashMap<>();
        for (PolicyEditor.Entry e : entries) {
            if (e.valid()) explicit.put(new MaterialForm(new MaterialId(e.material()), new FormId(e.form())), e.to());
        }
        return explicit;
    }

    /** Write the policy, regenerate the pack from it, reload, then reopen the GUI. */
    private static void writeAndReload(ServerPlayer player, PolicyWrite write, Component success) {
        MinecraftServer server = player.server;
        if (server.isDedicatedServer()) {
            player.sendSystemMessage(Component.translatable("message.materialnexus.read_only_apply"));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;

        try {
            List<PackContent.Effect> previous = PackContent.readManifest(MnxPaths.generated());
            write.run();
            var content = packContent(server, PolicyFiles.load(MnxPaths.policies()), previous);
            GeneratedPack.write(MnxPaths.generated(), content.files(), PackContent.toJson(content.effects()));
        } catch (IOException | RuntimeException e) {
            BUSY.set(false);
            LOGGER.error("Material Nexus policy write failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            BUSY.set(false);
            // The policy and pack are already written. A failure here comes from another mod's reload listener
            // after the data was swapped (e.g. IE arc recycling on live metal tag changes): report it, do not pretend
            // nothing happened, and still treat the changes as applied.
            if (error != null) {
                LOGGER.error("A reload listener failed after Material Nexus applied its changes", error);
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                player.sendSystemMessage(Component.translatable("message.materialnexus.reload_failed", cause.toString()));
            } else {
                player.sendSystemMessage(success);
            }
            PacketDistributor.sendToPlayer(player, OpenNexusPayload.forPlayer(player, true));
        }));
    }
}
