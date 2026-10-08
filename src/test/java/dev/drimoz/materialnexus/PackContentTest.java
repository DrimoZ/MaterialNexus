package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.PackContent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** What MNX-007 writes into a pack: only player decisions, only real alternatives, and stable across applies. */
class PackContentTest {
    /** Writing the pack reads the game version (pack_format); no FML bootstrap on the 1.20.1 test classpath. */
    @org.junit.jupiter.api.BeforeAll
    static void gameVersion() {
        net.minecraft.SharedConstants.tryDetectVersion();
    }

    private static final ResourceLocation INGOTS_TIN = ResourceLocation.fromNamespaceAndPath("forge", "ingots/tin");
    private static final ResourceLocation MEK = ResourceLocation.fromNamespaceAndPath("mekanism", "ingot_tin");
    private static final ResourceLocation MI = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "tin_ingot");
    private static final ResourceLocation MI_VARIANT = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "tin_ingot_alt");
    private static final ResourceLocation IE = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "ingot_tin");

    @Test
    void onlyPlayerDecisionsGenerateAndTheyStayStable(@TempDir Path root) throws IOException {
        Map<ResourceLocation, List<ResourceLocation>> tags = new HashMap<>(Map.of(INGOTS_TIN, List.of(MEK, MI, MI_VARIANT, IE)));

        // No policy: the default canonical is only a suggestion, nothing is generated.
        var none = TagDiscovery.discover(tags);
        assertTrue(PackContent.generate(CanonicalResolver.resolve(none, ResolutionPolicy.NONE), ResolutionPolicy.NONE).files().isEmpty());

        // Player picks Mekanism and asks for ingot conversion recipes.
        var policy = new ResolutionPolicy(List.of("mekanism"), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(new FormId("ingot")));
        var content = PackContent.generate(CanonicalResolver.resolve(none, policy), policy);
        // MI has two items here (same-mod variants): they are not alternatives, so never removed nor converted.
        assertEquals(List.of(
                new PackContent.Effect(PackContent.CONVERSION, MEK, IE),
                new PackContent.Effect(PackContent.ITEM_CONVERSION, MEK, IE),
                new PackContent.Effect(PackContent.TAG_REMOVE, INGOTS_TIN, IE)), content.effects());
        assertTrue(content.files().containsKey("data/forge/tags/items/ingots/tin.json"));
        assertTrue(content.files().get("data/forge/tags/items/ingots/tin.json").toString().contains("\"remove\":[\"immersiveengineering:ingot_tin\"]"));

        // After the reload the tag no longer contains IE; restoring from the manifest yields the same pack again.
        Path pack = root.resolve("generated");
        GeneratedPack.write(pack, content.files(), PackContent.toJson(content.effects()));
        Map<ResourceLocation, List<ResourceLocation>> afterReload = new HashMap<>(Map.of(INGOTS_TIN, List.of(MEK, MI, MI_VARIANT)));
        PackContent.restoreRemovedMembers(afterReload, PackContent.readManifest(pack));
        var again = PackContent.generate(CanonicalResolver.resolve(TagDiscovery.discover(afterReload), policy), policy);
        assertEquals(content.effects(), again.effects());
    }

    @Test
    void missingTagsAreAddedOnlyWhereThePackUsesThemAndStayStable(@TempDir Path root) throws IOException {
        ResourceLocation ingots = ResourceLocation.fromNamespaceAndPath("forge", "ingots");
        ResourceLocation modx = ResourceLocation.fromNamespaceAndPath("modx", "tin_ingot");
        ResourceLocation modxDouble = ResourceLocation.fromNamespaceAndPath("modx", "tin_double_ingot");
        // modx items are only known by name pattern; nobody uses forge:double_ingots, so that form gets no tag.
        Map<ResourceLocation, List<ResourceLocation>> before = Map.of(INGOTS_TIN, List.of(MEK), ingots, List.of(MEK),
                ResourceLocation.fromNamespaceAndPath("materialnexus", "pattern/ingots/tin"), List.of(modx),
                ResourceLocation.fromNamespaceAndPath("materialnexus", "pattern/double_ingots/tin"), List.of(modxDouble));
        var policy = new ResolutionPolicy(List.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(),
                dev.drimoz.materialnexus.core.policy.AlmostUnified.NONE, dev.drimoz.materialnexus.core.policy.ProcessRules.NONE, Set.of(), true, Map.of());

        var discovered = TagDiscovery.discover(before);
        var content = PackContent.generate(CanonicalResolver.resolve(discovered, policy), policy,
                dev.drimoz.materialnexus.core.policy.AlmostUnified.Ownership.ALL, PackContent.conventionMembers(discovered, before));
        assertEquals(List.of(new PackContent.Effect(PackContent.TAG_ADD, ingots, modx),
                new PackContent.Effect(PackContent.TAG_ADD, INGOTS_TIN, modx)), content.effects());
        assertEquals("{\"replace\":false,\"values\":[{\"id\":\"modx:tin_ingot\",\"required\":false}]}", content.files().get("data/forge/tags/items/ingots/tin.json").toString());

        // After the reload both tags hold modx; seen without Material Nexus, the same pack comes out again.
        Path pack = root.resolve("generated");
        GeneratedPack.write(pack, content.files(), PackContent.toJson(content.effects()));
        Map<ResourceLocation, List<ResourceLocation>> after = new HashMap<>(before);
        after.put(INGOTS_TIN, List.of(MEK, modx));
        after.put(ingots, List.of(MEK, modx));
        PackContent.restoreRemovedMembers(after, PackContent.readManifest(pack));
        var again = TagDiscovery.discover(after);
        assertEquals(content.effects(), PackContent.generate(CanonicalResolver.resolve(again, policy), policy,
                dev.drimoz.materialnexus.core.policy.AlmostUnified.Ownership.ALL, PackContent.conventionMembers(again, after)).effects());
    }

    @Test
    void playerTagEditsAreWrittenSeenByDiscoveryAndStayStable(@TempDir Path root) throws IOException {
        ResourceLocation modx = ResourceLocation.fromNamespaceAndPath("modx", "tin_ingot");
        var policy = dev.drimoz.materialnexus.datapack.PolicyFiles.parse(com.google.gson.JsonParser.parseString(
                "{\"tag_edits\": {\"tin/ingot\": {\"add\": [\"modx:tin_ingot\"], \"remove\": [\"immersiveengineering:ingot_tin\"]}}}"), Map.of());
        Map<ResourceLocation, List<ResourceLocation>> tags = new HashMap<>(Map.of(INGOTS_TIN, List.of(MEK, IE)));
        PackContent.addPlayerTags(tags, policy);
        var resolved = CanonicalResolver.resolve(TagDiscovery.discover(tags), policy);
        var tin = resolved.get(new dev.drimoz.materialnexus.core.domain.MaterialId("tin")).forms().get(new FormId("ingot"));
        // Removed: still listed, never unified. Added: an ordinary member.
        assertTrue(tin.notUnified().stream().anyMatch(n -> n.item().equals(IE) && n.reasonKey().endsWith("tag_removed")));
        assertTrue(tin.alternatives().contains(modx) || tin.canonical().orElseThrow().equals(modx));

        var content = PackContent.generate(resolved, policy);
        assertEquals(List.of(new PackContent.Effect(PackContent.TAG_ADD, INGOTS_TIN, modx),
                new PackContent.Effect(PackContent.TAG_REMOVE, INGOTS_TIN, IE)), content.effects());

        Path pack = root.resolve("generated");
        GeneratedPack.write(pack, content.files(), PackContent.toJson(content.effects()));
        Map<ResourceLocation, List<ResourceLocation>> after = new HashMap<>(Map.of(INGOTS_TIN, List.of(MEK, modx)));
        PackContent.restoreRemovedMembers(after, PackContent.readManifest(pack));
        PackContent.addPlayerTags(after, policy);
        assertEquals(content.effects(), PackContent.generate(CanonicalResolver.resolve(TagDiscovery.discover(after), policy), policy).effects());
    }
}
