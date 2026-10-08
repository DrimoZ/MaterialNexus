package dev.drimoz.materialnexus.core.discovery;

import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.conversion.ItemConversions;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;
import dev.drimoz.materialnexus.integration.ScriptSources;
import dev.drimoz.materialnexus.datapack.MaterialDefinitions;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs discovery once per server data load (startup and /reload), never per tick. */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class DiscoveryEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DiscoveryEvents() { }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        long start = System.nanoTime();
        try {
            Map<ResourceLocation, List<ResourceLocation>> tagMembers = new HashMap<>();
            event.getRegistryAccess().registryOrThrow(Registries.ITEM).getTags().forEach(pair ->
                    tagMembers.put(pair.getFirst().location(), pair.getSecond().stream()
                            .flatMap(holder -> holder.unwrapKey().stream())
                            .map(ResourceKey::location)
                            .toList()));

            // MNX-076: what scripts changed in the tags, before both views are completed the same way below.
            Map<ResourceLocation, List<ResourceLocation>> fileTags = new HashMap<>();
            ScriptSources.fileTags().forEach((tag, items) -> fileTags.put(tag, new java.util.ArrayList<>(items)));
            // No file view (listener not run): nothing to compare, rather than every tag entry read as added by a script.
            ScriptChanges scripts = new ScriptChanges(fileTags.isEmpty() ? List.of() : ScriptChanges.tagEdits(fileTags, tagMembers),
                    ScriptSources.takeRecipes());

            // ADR-010: see the tags as they were before our own removals, or the next apply would undo them.
            List<PackContent.Effect> applied = PackContent.readManifest(MnxPaths.generated()).stream()
                    .filter(e -> BuiltInRegistries.ITEM.containsKey(e.item())).toList();
            DiscoveredMaterials discovered = discover(tagMembers, applied);
            ResolutionPolicy policy = PolicyFiles.load(MnxPaths.policies());
            // Groups from the file tags: an item a script removed from its tag still belongs to its material/form.
            var decisions = scripts.equals(ScriptChanges.NONE) ? List.<ScriptDecisions.Decision>of()
                    : ScriptDecisions.infer(discover(fileTags, applied), scripts);
            // MNX-077: where they are written, read only when there is something to place.
            if (!decisions.isEmpty()) decisions = ScriptDecisions.locate(decisions, ScriptSources.serverScripts());
            ResolvedSnapshot previous = SnapshotManager.current();
            SnapshotManager.swap(new ResolvedSnapshot(previous.generation() + 1, Instant.now(), discovered,
                    CanonicalResolver.resolve(discovered, policy), scripts, decisions,
                    // tagMembers is now the pre-MNX view (discover restored it): what add_missing_tags compares against.
                    PackContent.conventionMembers(discovered, tagMembers)));
            // In-world conversion follows the applied pack, not the policy files (ADR-015).
            ItemConversions.install(PackContent.itemConversions(applied));
            ItemConversions.setViewerHiding(policy.almostUnified().mnxOwns(dev.drimoz.materialnexus.core.policy.AlmostUnified.Domain.VIEWER_HIDING,
                    net.minecraftforge.fml.ModList.get().isLoaded(dev.drimoz.materialnexus.core.policy.AlmostUnified.MOD_ID)));
            LOGGER.info("Material Nexus discovered {} materials ({} providers) in {} ms; scripts changed {} tag entries and {} recipes, deciding {} forms",
                    discovered.materials().size(), discovered.providerCount(), (System.nanoTime() - start) / 1_000_000,
                    scripts.tags().size(), scripts.recipes().size(), decisions.size());
        } catch (IOException | RuntimeException e) {
            // ADR-002: a failed analysis reports and changes nothing.
            LOGGER.error("Material Nexus discovery failed; snapshot left unchanged", e);
        }
    }

    /** Tags as discovery reads them: our own removals restored (ADR-010), untagged forms injected by name (MNX-040). */
    private static DiscoveredMaterials discover(Map<ResourceLocation, List<ResourceLocation>> tagMembers, List<PackContent.Effect> applied) {
        PackContent.restoreRemovedMembers(tagMembers, applied);
        dev.drimoz.materialnexus.datapack.FormPatterns.inject(tagMembers, BuiltInRegistries.ITEM.keySet(),
                dev.drimoz.materialnexus.datapack.FormPatterns.patterns(),
                dev.drimoz.materialnexus.datapack.FormPatterns.knownNames(tagMembers.keySet(), MaterialDefinitions.aliases().keySet()));
        return TagDiscovery.discover(tagMembers, MaterialDefinitions.aliases());
    }
}
