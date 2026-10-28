package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Grits read as dusts, and a flat form tag plus one material tag names the form, never more than the tags say. */
class TagCrossingTest {
    // What material_nexus/forms/common_tags.json declares, since JUnit does not load datapacks.
    @BeforeEach
    void declare() {
        TagDiscovery.setDataFolders(Map.of("grits", new FormId("dust")), List.of());
        TagDiscovery.setMaterialFolders(List.of("material"));
    }

    @AfterEach
    void reset() {
        TagDiscovery.setDataFolders(Map.of(), List.of());
        TagDiscovery.setMaterialFolders(List.of());
    }

    @Test
    void nothingIsCrossedUnlessDataDeclaresIt() {
        TagDiscovery.setMaterialFolders(List.of());
        var plate = rl("immersivegeology:plate_chromium");

        var discovered = TagDiscovery.discover(Map.of(rl("forge:plates"), List.of(plate), rl("forge:material/chromium"), List.of(plate)));

        assertTrue(discovered.materials().isEmpty());
    }

    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }

    @Test
    void gritsAreDustsAndTheDustFolderStaysTheCanonicalOne() {
        var discovered = TagDiscovery.discover(Map.of(rl("forge:grits/coal"), List.of(rl("voltex:coal_dust"))));

        assertEquals(1, discovered.providers(new MaterialId("coal"), new FormId("dust")).size());
        assertEquals(rl("forge:dusts/coal"), TagDiscovery.conventionTag(new MaterialId("coal"), new FormId("dust")).orElseThrow());
    }

    @Test
    void flatFormTagCrossedWithOneMaterialTag() {
        var plate = rl("immersivegeology:plate_chromium");
        var both = rl("immersivegeology:plate_alloy");
        var discovered = TagDiscovery.discover(Map.of(
                rl("forge:plates"), List.of(plate, both),
                rl("forge:material/chromium"), List.of(plate, both),
                rl("forge:material/nickel"), List.of(both)));

        var found = discovered.providers(new MaterialId("chromium"), new FormId("plate"));
        assertEquals(List.of(plate), found.stream().map(p -> p.resource()).toList());
        assertEquals(Confidence.INTEGRATION, found.get(0).evidence().get(0).confidence());
        // Two materials on one item: not guessed.
        assertTrue(discovered.providers(new MaterialId("nickel"), new FormId("plate")).isEmpty());
    }

    @Test
    void ownTagWinsOverTheCrossing() {
        var plate = rl("a:chromium_plate");
        var discovered = TagDiscovery.discover(Map.of(
                rl("forge:plates/chromium"), List.of(plate),
                rl("forge:plates"), List.of(plate),
                rl("forge:material/chromium"), List.of(plate)));

        var evidence = discovered.providers(new MaterialId("chromium"), new FormId("plate")).get(0).evidence();
        assertEquals(1, evidence.size());
        assertEquals(Confidence.MATERIAL_TAG, evidence.get(0).confidence());
    }
}
