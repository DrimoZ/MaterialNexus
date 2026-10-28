package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A second folder for one form (grits, as common_tags.json declares it) finds dusts, and the dust tag stays forge:dusts. */
class GritsFolderTest {
    @AfterEach
    void reset() {
        TagDiscovery.setDataFolders(Map.of(), List.of());
    }

    @Test
    void gritsAreDustsAndTheDustFolderStaysTheCanonicalOne() {
        TagDiscovery.setDataFolders(Map.of("grits", new FormId("dust")), List.of());

        var discovered = TagDiscovery.discover(Map.of(ResourceLocation.parse("forge:grits/chromium"),
                List.of(ResourceLocation.parse("immersivegeology:grit_chromium"))));

        assertEquals(1, discovered.providers(new MaterialId("chromium"), new FormId("dust")).size());
        assertEquals(ResourceLocation.parse("forge:dusts/chromium"), TagDiscovery.conventionTag(new MaterialId("chromium"), new FormId("dust")).orElseThrow());
    }
}
