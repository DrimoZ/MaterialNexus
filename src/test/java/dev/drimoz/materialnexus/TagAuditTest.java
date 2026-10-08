package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.diagnostics.TagAudit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-080: recipes asking for forge: tags or for material tags nothing is in, whatever the recipe format. */
class TagAuditTest {
    @Test
    void findsForgeAndEmptyMaterialTagsInAnyFormat() {
        ResourceLocation shaped = ResourceLocation.parse("modx:gear");
        ResourceLocation machine = ResourceLocation.parse("mody:press");
        var recipes = Map.of(
                shaped, JsonParser.parseString("{\"pattern\": [\"# #\"], \"key\": {\"#\": {\"tag\": \"forge:ingots/tin\"}}, \"result\": {\"id\": \"modx:gear\"}}"),
                machine, JsonParser.parseString("{\"input\": {\"ingredient\": \"#c:plates/osmium\"}, \"extra\": [{\"tag\": \"c:ingots/tin\"}, {\"tag\": \"c:ingots\"}]}"));
        Set<ResourceLocation> filled = Set.of(ResourceLocation.parse("c:ingots/tin"), ResourceLocation.parse("c:ingots"));

        var result = TagAudit.of(recipes, filled::contains, Set.of("ingots", "plates")::contains);

        assertEquals(Set.of(shaped), result.forge().get(ResourceLocation.parse("forge:ingots/tin")));
        assertTrue(result.forgeFilled().isEmpty());
        // Only the empty material tag; the folder tag c:ingots and the filled c:ingots/tin are fine.
        assertEquals(Set.of(ResourceLocation.parse("c:plates/osmium")), result.emptyMaterial().keySet());
    }
}
