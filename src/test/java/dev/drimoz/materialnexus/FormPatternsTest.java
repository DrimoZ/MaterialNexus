package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.datapack.FormPatterns;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Name patterns are declared data, and still only ever attach items to materials the tags already know. */
class FormPatternsTest {
    @Test
    void patternsOnlyAttachToKnownMaterials() {
        Map<ResourceLocation, List<ResourceLocation>> tags = new HashMap<>();
        tags.put(ResourceLocation.parse("c:ingots/aluminum"), List.of(ResourceLocation.parse("modern_industrialization:aluminum_ingot")));
        tags.put(ResourceLocation.parse("c:ingots/stainless_steel"), List.of(ResourceLocation.parse("modern_industrialization:stainless_steel_ingot")));
        var items = List.of(
                ResourceLocation.parse("modern_industrialization:aluminum_double_ingot"),
                ResourceLocation.parse("modern_industrialization:stainless_steel_double_ingot"),
                ResourceLocation.parse("modern_industrialization:steel_turbine_blade"),
                ResourceLocation.parse("othermod:aluminum_double_ingot"));
        var patterns = Map.of(new FormId("double_ingot"), List.of("modern_industrialization:{material}_double_ingot"),
                new FormId("blade"), List.of("modern_industrialization:{material}_blade"));

        FormPatterns.inject(tags, items, patterns, FormPatterns.knownNames(tags.keySet(), Set.of()));
        var discovered = TagDiscovery.discover(tags);

        var aluminum = discovered.providers(new MaterialId("aluminum"), new FormId("double_ingot"));
        assertEquals(List.of(ResourceLocation.parse("modern_industrialization:aluminum_double_ingot")), aluminum.stream().map(p -> p.resource()).toList());
        assertEquals(Confidence.INTEGRATION, aluminum.getFirst().evidence().getFirst().confidence());
        assertEquals(1, discovered.providers(new MaterialId("stainless_steel"), new FormId("double_ingot")).size());
        // "steel_turbine" is not a material any tag names: the blade is not attached anywhere.
        assertTrue(discovered.materials().keySet().stream().noneMatch(m -> m.name().equals("steel_turbine")));
    }
}
