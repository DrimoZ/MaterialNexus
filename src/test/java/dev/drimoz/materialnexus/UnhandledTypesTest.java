package dev.drimoz.materialnexus;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.diagnostics.UnhandledTypes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MNX-090: the report ranks the recipe types worth a format; a wrong count sends compat work to the wrong mod. */
class UnhandledTypesTest {
    private static ResourceLocation rl(String id) { return ResourceLocation.parse(id); }

    @Test
    void typesWithoutFormatAreRankedByTheRecipesThatMatter() {
        Map<ResourceLocation, JsonElement> recipes = Map.of(
                rl("thermal:a"), JsonParser.parseString("{\"type\":\"thermal:pulverizer\",\"ingredient\":{\"item\":\"minecraft:raw_copper\"},\"result\":[{\"item\":\"modb:copper_dust\"}]}"),
                rl("thermal:b"), JsonParser.parseString("{\"type\":\"thermal:pulverizer\",\"result\":[{\"item\":\"moda:copper_dust\"}]}"),
                rl("thermal:c"), JsonParser.parseString("{\"type\":\"thermal:pulverizer\",\"result\":[{\"item\":\"minecraft:gravel\"}]}"),
                rl("other:d"), JsonParser.parseString("{\"type\":\"other:press\",\"output\":\"moda:copper_dust\"}"),
                rl("minecraft:e"), JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"result\":{\"id\":\"modb:copper_dust\"}}"));
        var rows = UnhandledTypes.rank(recipes, type -> type.getNamespace().equals("minecraft"),
                Set.of(rl("moda:copper_dust"), rl("modb:copper_dust")), Set.of(rl("modb:copper_dust")));
        assertEquals(List.of(new UnhandledTypes.Row(rl("thermal:pulverizer"), 2, 1), new UnhandledTypes.Row(rl("other:press"), 1, 0)), rows);
    }
}
