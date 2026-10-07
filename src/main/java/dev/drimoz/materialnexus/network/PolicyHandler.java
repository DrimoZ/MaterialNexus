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
import dev.drimoz.materialnexus.item.CreatedItems;
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
        // An unknown preset id (removed datapack, forged packet) is simply ignored.
        java.util.Optional<com.google.gson.JsonObject> preset = request.preset().flatMap(dev.drimoz.materialnexus.datapack.Presets::get);
        List<PolicyEditor.Entry> entries = PolicyEditor.preview(SnapshotManager.current(), request.changes());
        List<PackContent.Effect> proposed;
        List<PackContent.Effect> current;
        List<CreatedItems.Entry> creations = creations(request);
        try {
            ResolutionPolicy policy = PolicyFiles.load(MnxPaths.policies(), preset).withExplicit(explicitChoices(entries))
                    .withProcesses(processes(request));
            current = PackContent.readManifest(MnxPaths.generated());
            proposed = packContent(player.server, policy, current).effects();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Material Nexus preview failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
            return;
        }
        List<PackContent.Effect> added = new java.util.ArrayList<>(proposed.stream().filter(e -> !current.contains(e)).toList());
        // Created items are not pack content (their tags come with the item, after the restart): listed for review only.
        creations.forEach(c -> added.add(new PackContent.Effect(PackContent.ITEM_CREATE, c.id(), c.tag())));
        List<PackContent.Effect> removed = current.stream().filter(e -> !proposed.contains(e)).toList();
        boolean valid = entries.stream().anyMatch(PolicyEditor.Entry::valid);

        var data = dataEdits(player, request, added, !request.apply());
        if (!request.apply()) {
            PacketDistributor.sendToPlayer(player, new PreviewPayload(entries, added, removed, request.preset().filter(id -> preset.isPresent())));
            return;
        }
        if (!valid && preset.isEmpty() && request.processes().isEmpty() && creations.isEmpty() && data.isEmpty() && added.isEmpty() && removed.isEmpty()) return;
        // With no valid choice, apply only regenerates the pack from the policy as written (e.g. edited by hand).
        writeAndReload(player, () -> {
                    // Data edits live under policies/: the same backup covers them, so it is taken for them too.
                    if (valid || preset.isPresent() || !request.processes().isEmpty() || !data.isEmpty()) {
                        PolicyEditor.apply(MnxPaths.policies(), entries, preset, processes(request));
                    }
                    if (!data.isEmpty()) dev.drimoz.materialnexus.datapack.EditableData.write(data);
                    // Not part of the policy backup: an item registered after a restart cannot be reverted by a reload.
                    if (!creations.isEmpty()) CreatedItems.add(itemsFile(), creations);
                },
                creations.isEmpty() ? Component.translatable("message.materialnexus.applied", added.size() + removed.size())
                        : Component.translatable("message.materialnexus.applied_restart", added.size() + removed.size()),
                !data.isEmpty());
    }

    /** Effect kinds listed in Preview for data edits (MNX-046); they are files of the edits pack, not generated content. */
    static final String DATA_EDIT = "data_edit";
    static final String DATA_RESET = "data_reset";
    static final String DATA_INVALID = "data_invalid";

    /**
     * The valid data edits of a request, keyed by kind and id; each one is listed in {@code effects}. An invalid edit is
     * listed too, and its reason sent to the player when previewing; it is never written.
     */
    private static Map<Map.Entry<dev.drimoz.materialnexus.datapack.EditableData.Kind, ResourceLocation>, java.util.Optional<String>> dataEdits(
            ServerPlayer player, PreviewRequest request, List<PackContent.Effect> effects, boolean tell) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryAccess());
        Map<Map.Entry<dev.drimoz.materialnexus.datapack.EditableData.Kind, ResourceLocation>, java.util.Optional<String>> valid = new java.util.LinkedHashMap<>();
        new java.util.TreeMap<>(request.data()).forEach((key, text) -> {
            String[] parts = key.split("\\|", 2);
            var kind = parts.length == 2 ? dev.drimoz.materialnexus.datapack.EditableData.Kind.byKey(parts[0]) : java.util.Optional.<dev.drimoz.materialnexus.datapack.EditableData.Kind>empty();
            ResourceLocation id = parts.length == 2 ? ResourceLocation.tryParse(parts[1]) : null;
            if (kind.isEmpty() || id == null) return;
            ResourceLocation tag = ResourceLocation.fromNamespaceAndPath(dev.drimoz.materialnexus.MaterialNexus.MOD_ID, kind.get().key);
            java.util.Optional<String> error = text.flatMap(t -> dev.drimoz.materialnexus.datapack.EditableData.validate(kind.get(), t,
                    json -> net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, json).isSuccess()));
            if (error.isPresent()) {
                effects.add(new PackContent.Effect(DATA_INVALID, id, tag));
                if (tell) player.sendSystemMessage(Component.translatable("message.materialnexus.data_invalid", kind.get().key, id.toString(), error.get()));
                return;
            }
            effects.add(new PackContent.Effect(text.isPresent() ? DATA_EDIT : DATA_RESET, id, tag));
            valid.put(Map.entry(kind.get(), id), text);
        });
        return valid;
    }

    /** The form's process rule as written, plus the routes already found in recipes (examples are read on demand). */
    static void process(ServerPlayer player, String formName) {
        var form = FormId.read(formName).result();
        if (form.isEmpty()) return;
        try {
            ResolutionPolicy policy = PolicyFiles.load(MnxPaths.policies());
            RecipeFormats formats = RecipeFormats.load(player.server.getResourceManager());
            var sources = RecipeSources.known(player.server, formats, RecipeRewrites.overridden(PackContent.readManifest(MnxPaths.generated())));
            var examples = dev.drimoz.materialnexus.datapack.ProcessPlanner.examples(sources, formats,
                    CanonicalResolver.resolve(SnapshotManager.current().discovered(), policy), form.get());
            PacketDistributor.sendToPlayer(player, new ProcessPayload(formName, java.util.Optional.ofNullable(policy.processes().forms().get(form.get())),
                    new dev.drimoz.materialnexus.core.policy.ProcessRules.Rule(examples, false, false)));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Material Nexus process query failed", e);
            player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
        }
    }

    static void revert(ServerPlayer player) {
        if (!PolicyEditor.canRevert(MnxPaths.policies())) return;
        writeAndReload(player, () -> PolicyEditor.revert(MnxPaths.policies()),
                Component.translatable("message.materialnexus.reverted"));
    }

    /** The whole generated pack for a policy: tags and conversions, then the recipes they imply. */
    private static java.nio.file.Path itemsFile() {
        return MnxPaths.root().resolve(CreatedItems.FILE);
    }

    /** "material/form" requests from the client, kept only when discovery confirms the form is missing (MNX-039). */
    private static List<CreatedItems.Entry> creations(PreviewRequest request) {
        var snapshot = SnapshotManager.current();
        List<CreatedItems.Entry> valid = new java.util.ArrayList<>();
        for (String c : request.creations()) {
            String[] parts = c.split("/", 2);
            if (parts.length != 2) continue;
            var material = dev.drimoz.materialnexus.core.domain.MaterialId.read(parts[0]).result().map(snapshot.materials()::get).orElse(null);
            CreatedItems.validate(snapshot.discovered(), material, parts[0], parts[1]).ifPresent(valid::add);
        }
        return valid;
    }

    private static PackContent.Content packContent(MinecraftServer server, ResolutionPolicy policy, List<PackContent.Effect> applied) {
        boolean auPresent = net.neoforged.fml.ModList.get().isLoaded(dev.drimoz.materialnexus.core.policy.AlmostUnified.MOD_ID);
        var resolved = CanonicalResolver.resolve(SnapshotManager.current().discovered(), policy);
        RecipeFormats formats = RecipeFormats.load(server.getResourceManager());
        PackContent.Content content = PackContent.full(resolved, policy, auPresent, (conversions, ownership) -> RecipeRewrites.plan(
                RecipeSources.collect(server, conversions, formats, RecipeRewrites.overridden(applied)), conversions, formats, ownership));
        if (!policy.processes().isEmpty()) {
            var items = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ITEM);
            content = PackContent.withProcesses(content, dev.drimoz.materialnexus.datapack.ProcessPlanner.plan(
                    RecipeSources.known(server, formats, RecipeRewrites.overridden(applied)), formats, resolved, policy,
                    dev.drimoz.materialnexus.datapack.ProcessTemplates.templates(),
                    tag -> items.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag)).map(t -> t.size() > 0).orElse(false)));
        }
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        return PackContent.withValidRecipes(content, json -> net.minecraft.world.item.crafting.Recipe.CODEC.parse(ops, json).isSuccess());
    }

    /** Form names were validated when the packet was decoded. */
    private static Map<FormId, dev.drimoz.materialnexus.core.policy.ProcessRules.Rule> processes(PreviewRequest request) {
        Map<FormId, dev.drimoz.materialnexus.core.policy.ProcessRules.Rule> edits = new HashMap<>();
        request.processes().forEach((form, rule) -> edits.put(new FormId(form), rule));
        return edits;
    }

    private static Map<MaterialForm, ResourceLocation> explicitChoices(List<PolicyEditor.Entry> entries) {
        Map<MaterialForm, ResourceLocation> explicit = new HashMap<>();
        for (PolicyEditor.Entry e : entries) {
            if (e.valid()) explicit.put(new MaterialForm(new MaterialId(e.material()), new FormId(e.form())), e.to());
        }
        return explicit;
    }

    private static void writeAndReload(ServerPlayer player, PolicyWrite write, Component success) {
        writeAndReload(player, write, success, false);
    }

    /**
     * Write the policy, regenerate the pack from it, reload, then reopen the GUI. With {@code dataChanged} (MNX-046) the
     * pack is generated a second time after the reload, from the data that reload just loaded (patterns, templates...).
     */
    private static void writeAndReload(ServerPlayer player, PolicyWrite write, Component success, boolean dataChanged) {
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

        reload(player, success, dataChanged);
    }

    private static void reload(ServerPlayer player, Component success, boolean regenerateAfter) {
        MinecraftServer server = player.server;
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            if (regenerateAfter && error == null) {
                try {
                    var content = packContent(server, PolicyFiles.load(MnxPaths.policies()), PackContent.readManifest(MnxPaths.generated()));
                    GeneratedPack.write(MnxPaths.generated(), content.files(), PackContent.toJson(content.effects()));
                    reload(player, success, false);
                    return;
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Material Nexus could not regenerate the pack after a data edit", e);
                    player.sendSystemMessage(Component.translatable("message.materialnexus.apply_failed", e.getMessage()));
                }
            }
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
