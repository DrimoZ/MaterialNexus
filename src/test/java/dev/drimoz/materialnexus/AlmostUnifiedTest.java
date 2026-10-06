package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** ADR-012: two unifiers must never act on the same domain by accident. */
class AlmostUnifiedTest {
    private static final ResourceLocation INGOTS_TIN = ResourceLocation.fromNamespaceAndPath("c", "ingots/tin");
    private static final ResourceLocation MEK = ResourceLocation.fromNamespaceAndPath("mekanism", "ingot_tin");
    private static final ResourceLocation MI = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "tin_ingot");

    private static List<String> kinds(String global, boolean auPresent, List<AlmostUnified.Ownership> seen) {
        var policy = PolicyFiles.parse(JsonParser.parseString(global), Map.of());
        var discovered = TagDiscovery.discover(Map.of(INGOTS_TIN, List.of(MEK, MI)));
        var content = PackContent.full(CanonicalResolver.resolve(discovered, policy), policy, auPresent,
                (conversions, ownership) -> {
                    seen.add(ownership);
                    return new PackContent.Content(Map.of(), List.of());
                });
        return content.effects().stream().map(e -> e.kind() + (e.kind().equals(PackContent.ALMOST_UNIFIED) ? ":" + e.target().getPath() : "")).toList();
    }

    @Test
    void domainsLeftToAlmostUnifiedGenerateNothing() {
        String unify = "\"mod_priority\":[\"mekanism\"]";
        List<AlmostUnified.Ownership> seen = new ArrayList<>();

        assertTrue(kinds("{" + unify + "}", false, seen).contains(PackContent.TAG_REMOVE), "without AU, Material Nexus owns everything");
        assertEquals(AlmostUnified.Ownership.ALL, seen.getLast());

        List<String> unarbitrated = kinds("{" + unify + "}", true, seen);
        assertFalse(unarbitrated.contains(PackContent.TAG_REMOVE), "AU unifies by default: an unarbitrated domain is left to it");
        assertTrue(unarbitrated.containsAll(List.of("almost_unified:tags", "almost_unified:output_rewrite",
                "almost_unified:recipe_disable", "almost_unified:viewer_hiding")), unarbitrated.toString());
        assertEquals(new AlmostUnified.Ownership(false, false, false, false), seen.getLast());

        List<String> partial = kinds("{" + unify + ",\"almost_unified\":{\"tags\":\"mnx\",\"output_rewrite\":\"au\"}}", true, seen);
        assertTrue(partial.contains(PackContent.TAG_REMOVE));
        assertFalse(partial.contains("almost_unified:tags"));
        assertEquals(new AlmostUnified.Ownership(true, false, false, false), seen.getLast());

        assertThrows(IllegalArgumentException.class, () -> kinds("{\"almost_unified\":{\"tags\":\"both\"}}", true, seen));
    }
}
