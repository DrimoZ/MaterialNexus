package dev.drimoz.materialnexus;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.materialnexus.core.domain.MaterialDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Material definitions are hand-written by pack authors: they must round-trip and reject bad names cleanly. */
class MaterialIdCodecTest {
    @Test
    void definitionRoundTripsAndRejectsInvalidNames() {
        JsonElement json = JsonParser.parseString("{\"id\":\"copper\",\"forms\":[\"ingot\",\"plate\"],\"aliases\":[\"cuivre\"]}");
        MaterialDefinition def = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(json, MaterialDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow());

        assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"c:tin\"}")).isError());
        assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":\"tin\",\"forms\":[\"Plate\"]}")).isError());
    }
}
