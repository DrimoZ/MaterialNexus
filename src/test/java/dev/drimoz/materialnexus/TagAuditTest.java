package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.diagnostics.TagAudit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-080: recipes asking for c: tags (forge: on 1.20.1) or for material tags nothing is in, whatever the recipe format. */
class TagAuditTest {
    @Test
    void findsForgeAndEmptyMaterialTagsInAnyFormat() {
        ResourceLocation shaped = ResourceLocation.parse("modx:gear");
        ResourceLocation machine = ResourceLocation.parse("mody:press");
        var recipes = Map.of(
                shaped, JsonParser.parseString("{\"pattern\": [\"# #\"], \"key\": {\"#\": {\"tag\": \"c:ingots/tin\"}}, \"result\": {\"id\": \"modx:gear\"}}"),
                machine, JsonParser.parseString("{\"input\": {\"ingredient\": \"#forge:plates/osmium\"}, \"extra\": [{\"tag\": \"forge:ingots/tin\"}, {\"tag\": \"forge:ingots\"}]}"));
        Set<ResourceLocation> filled = Set.of(ResourceLocation.parse("forge:ingots/tin"), ResourceLocation.parse("forge:ingots"));

        var result = TagAudit.of(recipes, filled::contains, Set.of("ingots", "plates")::contains);

        assertEquals(Set.of(shaped), result.forge().get(ResourceLocation.parse("c:ingots/tin")));
        assertTrue(result.forgeFilled().isEmpty());
        // Only the empty material tag; the folder tag forge:ingots and the filled forge:ingots/tin are fine.
        assertEquals(Set.of(ResourceLocation.parse("forge:plates/osmium")), result.emptyMaterial().keySet());
    }
}
