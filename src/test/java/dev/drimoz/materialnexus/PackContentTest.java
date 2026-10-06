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
    private static final ResourceLocation INGOTS_TIN = ResourceLocation.fromNamespaceAndPath("c", "ingots/tin");
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
        assertTrue(content.files().containsKey("data/c/tags/item/ingots/tin.json"));
        assertTrue(content.files().get("data/c/tags/item/ingots/tin.json").toString().contains("\"remove\":[\"immersiveengineering:ingot_tin\"]"));

        // After the reload the tag no longer contains IE; restoring from the manifest yields the same pack again.
        Path pack = root.resolve("generated");
        GeneratedPack.write(pack, content.files(), PackContent.toJson(content.effects()));
        Map<ResourceLocation, List<ResourceLocation>> afterReload = new HashMap<>(Map.of(INGOTS_TIN, List.of(MEK, MI, MI_VARIANT)));
        PackContent.restoreRemovedMembers(afterReload, PackContent.readManifest(pack));
        var again = PackContent.generate(CanonicalResolver.resolve(TagDiscovery.discover(afterReload), policy), policy);
        assertEquals(content.effects(), again.effects());
    }
}
