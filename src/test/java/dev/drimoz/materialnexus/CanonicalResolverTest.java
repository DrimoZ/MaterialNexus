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
import java.util.Set;

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
        assertEquals(A, resolve(members, ResolutionPolicy.NONE).canonical().orElseThrow());

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
        assertEquals(ResourceLocation.fromNamespaceAndPath("ghost", "plate"), fallback.ignoredOverride().orElseThrow());
    }

    @Test
    void resultDoesNotDependOnInputOrder() {
        var policy = new ResolutionPolicy(List.of("modx", "modb"), Map.of(), Map.of(), Map.of());
        assertEquals(resolve(List.of(A, B, C), policy), resolve(List.of(C, B, A), policy));
    }

    /** MNX-027: the false duplicates found in a real pack are sorted out, listed, and never dropped. */
    @Test
    void falseDuplicatesAreSortedOutNotRemoved() {
        ResourceLocation stick = ResourceLocation.withDefaultNamespace("stick");
        ResourceLocation treated = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "stick_treated");
        ResourceLocation certus = ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal");
        ResourceLocation charged = ResourceLocation.fromNamespaceAndPath("ae2", "charged_certus_quartz_crystal");
        ResourceLocation createZinc = ResourceLocation.fromNamespaceAndPath("create", "zinc_ore");
        ResourceLocation createDeepZinc = ResourceLocation.fromNamespaceAndPath("create", "deepslate_zinc_ore");
        ResourceLocation otherDeepZinc = ResourceLocation.fromNamespaceAndPath("othermod", "deepslate_zinc_ore");
        var discovered = TagDiscovery.discover(Map.of(
                ResourceLocation.fromNamespaceAndPath("c", "rods/wooden"), List.of(stick, treated),
                ResourceLocation.fromNamespaceAndPath("c", "rods/treated_wood"), List.of(treated),
                ResourceLocation.fromNamespaceAndPath("c", "gems/certus_quartz"), List.of(certus, charged),
                ResourceLocation.fromNamespaceAndPath("c", "ores/zinc"), List.of(createZinc, createDeepZinc, otherDeepZinc),
                ResourceLocation.fromNamespaceAndPath("c", "ores_in_ground/deepslate"), List.of(createDeepZinc)));
        var resolved = CanonicalResolver.resolve(discovered, ResolutionPolicy.NONE);
        ResolvedForm wooden = resolved.get(new MaterialId("wooden")).forms().get(new FormId("rod"));
        ResolvedForm gem = resolved.get(new MaterialId("certus_quartz")).forms().get(new FormId("gem"));
        var zinc = resolved.get(new MaterialId("zinc")).forms();

        // Treated stick is also tagged as a more specific material: listed, not a duplicate of the vanilla stick.
        assertEquals(stick, wooden.canonical().orElseThrow());
        assertEquals(List.of(), wooden.alternatives());
        assertEquals(List.of(treated), wooden.notUnified().stream().map(ResolvedForm.NotUnified::item).toList());

        // Two items of the same mod are variants: nothing to unify.
        assertTrue(gem.canonical().isEmpty());
        assertEquals(2, gem.notUnified().size());

        // Ores: stone and deepslate are separate forms; the same rock from two mods is a real duplicate.
        assertEquals(createZinc, zinc.get(new FormId("ore")).canonical().orElseThrow());
        ResolvedForm deep = zinc.get(new FormId("deepslate_ore"));
        assertEquals(1, deep.alternatives().size(), "create and othermod deepslate zinc ores are duplicates");

        // Exclusion leaves everything listed and untouched.
        var excluded = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of(), Set.of(new MaterialId("wooden")), Set.of());
        ResolvedForm woodenExcluded = CanonicalResolver.resolve(discovered, excluded).get(new MaterialId("wooden")).forms().get(new FormId("rod"));
        assertTrue(woodenExcluded.canonical().isEmpty());
        assertEquals(2, woodenExcluded.notUnified().size());
    }

    /** MNX-047, from a real pack: mods tag other items under a material; the names give them away. */
    @Test
    void namesTellVariantsFromDuplicates() {
        var rl = (java.util.function.Function<String, ResourceLocation>) ResourceLocation::parse;
        Map<ResourceLocation, List<ResourceLocation>> tags = Map.of(
                rl.apply("c:gems/amethyst"), List.of(rl.apply("minecraft:amethyst_shard"), rl.apply("remin:yellow_amethyst")),
                rl.apply("c:gems/quartz"), List.of(rl.apply("minecraft:quartz")),
                rl.apply("c:gems/milky_quartz"), List.of(rl.apply("minecraft:quartz"), rl.apply("remin:milky_quartz")),
                rl.apply("c:plates/plastic"), List.of(rl.apply("oritech:plastic_sheet"), rl.apply("immersiveengineering:plate_duroplast")),
                rl.apply("c:rods/wooden"), List.of(rl.apply("minecraft:stick"), rl.apply("silentgear:netherwood_stick")),
                // Real duplicates stay duplicates: adjectives and alternative spellings of the material are fine.
                rl.apply("c:plates/gold"), List.of(rl.apply("create:golden_sheet"), rl.apply("immersiveengineering:plate_gold")),
                rl.apply("c:ingots/aluminum"), List.of(rl.apply("remin:aluminium_ingot"), rl.apply("immersiveengineering:ingot_aluminum")),
                rl.apply("c:storage_blocks/raw_osmium"), List.of(rl.apply("mekanism:block_raw_osmium"), rl.apply("othermod:raw_osmium_block")));
        var resolved = CanonicalResolver.resolve(TagDiscovery.discover(tags), ResolutionPolicy.NONE);
        java.util.function.BiFunction<String, String, ResolvedForm> form = (m, f) -> resolved.get(new MaterialId(m)).forms().get(new FormId(f));

        assertTrue(form.apply("amethyst", "gem").alternatives().isEmpty());
        assertEquals("materialnexus.not_unified.named_variant", form.apply("amethyst", "gem").notUnified().getFirst().reasonKey());
        assertEquals(rl.apply("remin:milky_quartz"), form.apply("milky_quartz", "gem").canonical().orElseThrow());
        assertTrue(form.apply("milky_quartz", "gem").alternatives().isEmpty(), "vanilla quartz is set aside (more specific or named after quartz)");
        assertTrue(form.apply("plastic", "plate").alternatives().isEmpty());
        assertEquals(rl.apply("minecraft:stick"), form.apply("wooden", "rod").canonical().orElseThrow());
        assertTrue(form.apply("wooden", "rod").alternatives().isEmpty());

        assertEquals(1, form.apply("gold", "plate").alternatives().size());
        assertEquals(1, form.apply("aluminum", "ingot").alternatives().size());
        assertEquals(1, form.apply("osmium", "raw_block").alternatives().size());
    }

    private static void assertResolved(ResourceLocation expected, PolicyPrecedence source, ResolvedForm actual) {
        assertEquals(expected, actual.canonical().orElseThrow(), actual.reasonKey());
        assertEquals(source, actual.source(), actual.reasonKey());
    }
}
