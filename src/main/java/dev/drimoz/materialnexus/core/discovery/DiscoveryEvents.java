package dev.drimoz.materialnexus.core.discovery;

import com.mojang.logging.LogUtils;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs discovery once per server data load (startup and /reload), never per tick. */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID)
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

            DiscoveredMaterials discovered = TagDiscovery.discover(tagMembers);
            ResolvedSnapshot previous = SnapshotManager.current();
            SnapshotManager.swap(new ResolvedSnapshot(previous.generation() + 1, Instant.now(), discovered,
                    CanonicalResolver.resolve(discovered, ResolutionPolicy.NONE)));
            LOGGER.info("Material Nexus discovered {} materials ({} providers) in {} ms",
                    discovered.materials().size(), discovered.providerCount(), (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            // ADR-002: a failed analysis reports and changes nothing.
            LOGGER.error("Material Nexus discovery failed; snapshot left unchanged", e);
        }
    }
}
