package dev.drimoz.materialnexus;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialDefinition;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialIdCodecTest {
    @Test
    void namesAreValidated() {
        assertEquals("rose_gold", new MaterialId("rose_gold").name());
        for (String bad : new String[] {"", "Copper", "c:copper", "copper/plate", "dark-steel"}) {
            assertThrows(IllegalArgumentException.class, () -> new MaterialId(bad), bad);
            assertTrue(MaterialId.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString('"' + bad + '"')).isError(), bad);
        }
    }

    @Test
    void definitionRoundTripsAndDefaultsOptionalFields() {
        JsonElement json = JsonParser.parseString("{\"id\":\"copper\",\"forms\":[\"ingot\",\"plate\"],\"aliases\":[\"cuivre\"]}");
        MaterialDefinition def = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(new FormId("ingot"), new FormId("plate")), def.forms());
        assertEquals(json, MaterialDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow());

        MaterialDefinition bare = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"tin\"}")).getOrThrow();
        assertEquals(List.of(), bare.forms());
        assertEquals(List.of(), bare.aliases());

        assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"tin\",\"forms\":[\"Plate\"]}")).isError());
    }

    @Test
    void providerConfidenceIsStrongestEvidence() {
        Provider p = new Provider(ResourceLocation.fromNamespaceAndPath("create", "copper_sheet"),
                new MaterialId("copper"), new FormId("plate"), List.of(
                        new DiscoveryEvidence(Confidence.CONSERVATIVE_HEURISTIC, "name"),
                        new DiscoveryEvidence(Confidence.MATERIAL_TAG, "#c:plates/copper")));
        assertEquals(Confidence.MATERIAL_TAG, p.confidence());
        assertEquals("create", p.sourceMod());
        assertTrue(Confidence.EXPLICIT_DEFINITION.isStrongerThan(Confidence.MATERIAL_TAG));
    }
}
