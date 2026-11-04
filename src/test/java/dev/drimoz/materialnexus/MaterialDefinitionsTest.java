package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.datapack.MaterialDefinitions;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-033: an alias merges two spellings of one material; a contested alias keeps its first claim. */
class MaterialDefinitionsTest {
    @Test
    void aliasesMergeMaterialsExplicitly() {
        var aliases = MaterialDefinitions.parse(Map.of(
                Identifier.fromNamespaceAndPath("materialnexus", "aluminum"), JsonParser.parseString("{\"id\":\"aluminum\",\"aliases\":[\"aluminium\"]}"),
                Identifier.fromNamespaceAndPath("zpack", "other"), JsonParser.parseString("{\"id\":\"bauxite\",\"aliases\":[\"aluminium\"]}")));
        assertEquals(Map.of("aluminium", new MaterialId("aluminum")), aliases);

        Identifier a = Identifier.fromNamespaceAndPath("moda", "aluminum_ingot");
        Identifier b = Identifier.fromNamespaceAndPath("modb", "aluminium_ingot");
        var discovered = TagDiscovery.discover(Map.of(
                Identifier.fromNamespaceAndPath("c", "ingots/aluminum"), List.of(a),
                Identifier.fromNamespaceAndPath("c", "ingots/aluminium"), List.of(b)), aliases);
        assertEquals(2, discovered.providers(new MaterialId("aluminum"), new FormId("ingot")).size());
        assertFalse(discovered.materials().containsKey(new MaterialId("aluminium")));
    }
}
