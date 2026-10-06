package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** ADR-006 precedence and MNX-004 determinism: the two things that silently corrupt a pack if wrong. */
class CanonicalResolverTest {
    private static final MaterialId COPPER = new MaterialId("copper");
    private static final MaterialForm COPPER_PLATE = new MaterialForm(COPPER, new FormId("plate"));
    private static final ResourceLocation PLATES_COPPER = ResourceLocation.fromNamespaceAndPath("c", "plates/copper");
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("moda", "copper_plate");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("modb", "copper_plate");
    private static final ResourceLocation C = ResourceLocation.fromNamespaceAndPath("modc", "copper_plate");

    private static ResolvedForm resolve(List<ResourceLocation> members, ResolutionPolicy policy) {
        var discovered = TagDiscovery.discover(Map.of(PLATES_COPPER, members));
        return CanonicalResolver.resolve(discovered, policy).get(COPPER).forms().get(COPPER_PLATE.form());
    }

    @Test
    void eachPrecedenceLevelOverridesTheOneBelow() {
        List<ResourceLocation> members = List.of(A, B, C);
        assertEquals(A, resolve(members, ResolutionPolicy.NONE).canonical());

        var global = new ResolutionPolicy(List.of("modc"), Map.of(), Map.of(), Map.of());
        assertResolved(C, PolicyPrecedence.GLOBAL, resolve(members, global));

        var material = new ResolutionPolicy(List.of("modc"), Map.of(COPPER, List.of("modb")), Map.of(), Map.of());
        assertResolved(B, PolicyPrecedence.MATERIAL, resolve(members, material));

        var form = new ResolutionPolicy(List.of("modc"), Map.of(COPPER, List.of("modb")), Map.of(COPPER_PLATE, List.of("modc")), Map.of());
        assertResolved(C, PolicyPrecedence.FORM, resolve(members, form));

        var explicit = new ResolutionPolicy(List.of("modc"), Map.of(COPPER, List.of("modb")), Map.of(COPPER_PLATE, List.of("modc")), Map.of(COPPER_PLATE, A));
        ResolvedForm top = resolve(members, explicit);
        assertResolved(A, PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE, top);
        assertEquals(List.of(B, C), top.alternatives());

        // An explicit override naming an undiscovered item falls through instead of inventing a provider.
        var unknown = new ResolutionPolicy(List.of(), Map.of(), Map.of(COPPER_PLATE, List.of("modc")),
                Map.of(COPPER_PLATE, ResourceLocation.fromNamespaceAndPath("ghost", "plate")));
        ResolvedForm fallback = resolve(members, unknown);
        assertResolved(C, PolicyPrecedence.FORM, fallback);
        assertTrue(fallback.reason().contains("ignored"), fallback.reason());
    }

    @Test
    void resultDoesNotDependOnInputOrder() {
        var policy = new ResolutionPolicy(List.of("modx", "modb"), Map.of(), Map.of(), Map.of());
        assertEquals(resolve(List.of(A, B, C), policy), resolve(List.of(C, B, A), policy));
    }

    private static void assertResolved(ResourceLocation expected, PolicyPrecedence source, ResolvedForm actual) {
        assertEquals(expected, actual.canonical(), actual.reason());
        assertEquals(source, actual.source(), actual.reason());
    }
}
