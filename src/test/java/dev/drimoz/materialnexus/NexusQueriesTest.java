package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.NexusQueries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Client requests are untrusted input: out-of-range pages and bogus names must never throw or leak. */
class NexusQueriesTest {
    @Test
    void untrustedRequestsAreClampedOrIgnored() {
        Map<ResourceLocation, List<ResourceLocation>> tags = new HashMap<>();
        for (int i = 0; i < NexusQueries.PAGE_SIZE + 5; i++) {
            tags.put(ResourceLocation.fromNamespaceAndPath("forge", "ingots/metal" + i),
                    List.of(ResourceLocation.fromNamespaceAndPath("moda", "metal" + i), ResourceLocation.fromNamespaceAndPath("modb", "metal" + i)));
        }
        var discovered = TagDiscovery.discover(tags);
        var snapshot = new ResolvedSnapshot(1, Instant.EPOCH, discovered, CanonicalResolver.resolve(discovered, ResolutionPolicy.NONE));

        MaterialListPayload last = NexusQueries.listPage(snapshot, Integer.MAX_VALUE, "");
        assertEquals(1, last.page());
        assertEquals(5, last.entries().size());
        assertEquals(0, NexusQueries.listPage(snapshot, -7, "").page());
        assertEquals(1, last.entries().get(0).duplicateForms());

        assertEquals(1, NexusQueries.listPage(snapshot, 0, "  METAL516 ").totalMatches());
        assertTrue(NexusQueries.detail(snapshot, "../etc").isEmpty());
        assertTrue(NexusQueries.detail(snapshot, "unknown").isEmpty());
        assertEquals(1, NexusQueries.detail(snapshot, "metal3").orElseThrow().forms().size());

        // MNX-035: the same snapshot seen by form; one ingot card per material, each keyed by its material.
        var byForm = NexusQueries.listPage(snapshot, 0, "", true);
        assertEquals(1, byForm.totalMatches());
        assertEquals(NexusQueries.PAGE_SIZE + 5, byForm.entries().get(0).duplicateForms());
        var ingots = NexusQueries.detail(snapshot, "ingot", true).orElseThrow();
        assertEquals(NexusQueries.PAGE_SIZE + 5, ingots.forms().size());
        assertTrue(ingots.forms().stream().allMatch(v -> v.form().equals("ingot") && v.material().startsWith("metal")));
        assertTrue(NexusQueries.detail(snapshot, "../ingot", true).isEmpty());
    }
}
