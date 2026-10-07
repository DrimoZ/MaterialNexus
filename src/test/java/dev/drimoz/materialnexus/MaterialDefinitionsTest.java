package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.datapack.MaterialDefinitions;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-033: an alias merges two spellings of one material; a contested alias keeps its first claim. */
class MaterialDefinitionsTest {
    @Test
    void aliasesMergeMaterialsExplicitly() {
        var aliases = MaterialDefinitions.parse(Map.of(
                ResourceLocation.fromNamespaceAndPath("materialnexus", "aluminum"), JsonParser.parseString("{\"id\":\"aluminum\",\"aliases\":[\"aluminium\"]}"),
                ResourceLocation.fromNamespaceAndPath("zpack", "other"), JsonParser.parseString("{\"id\":\"bauxite\",\"aliases\":[\"aluminium\"]}")));
        assertEquals(Map.of("aluminium", new MaterialId("aluminum")), aliases);

        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("moda", "aluminum_ingot");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("modb", "aluminium_ingot");
        var discovered = TagDiscovery.discover(Map.of(
                ResourceLocation.fromNamespaceAndPath("forge", "ingots/aluminum"), List.of(a),
                ResourceLocation.fromNamespaceAndPath("forge", "ingots/aluminium"), List.of(b)), aliases);
        assertEquals(2, discovered.providers(new MaterialId("aluminum"), new FormId("ingot")).size());
        assertFalse(discovered.materials().containsKey(new MaterialId("aluminium")));
    }
}
