package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.item.CreatedItems;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A created item is a permanent registry entry: only ever for a real material, a missing form, a known template. */
class CreatedItemsTest {
    @Test
    void onlyMissingTemplatedFormsOfRealMaterials() {
        var tags = Map.of(
                ResourceLocation.parse("c:ingots/netherite"), List.of(ResourceLocation.parse("minecraft:netherite_ingot")),
                ResourceLocation.parse("c:rods/iron"), List.of(ResourceLocation.parse("immersiveengineering:stick_iron")));
        var discovered = TagDiscovery.discover(tags);
        var resolved = CanonicalResolver.resolve(discovered, ResolutionPolicy.NONE);
        var netherite = resolved.get(new MaterialId("netherite"));

        var rod = CreatedItems.validate(discovered, netherite, "netherite", "rod").orElseThrow();
        assertEquals(ResourceLocation.parse("materialnexus:netherite_rod"), rod.id());
        assertEquals(ResourceLocation.parse("c:rods/netherite"), rod.tag());
        assertEquals(ResourceLocation.parse("minecraft:netherite_ingot"), rod.colorFrom().orElseThrow());

        assertTrue(CreatedItems.validate(discovered, netherite, "netherite", "ingot").isEmpty(), "form already present");
        assertTrue(CreatedItems.validate(discovered, netherite, "netherite", "ore").isEmpty(), "no template for ores");
        assertTrue(CreatedItems.validate(discovered, null, "unobtainium", "rod").isEmpty(), "unknown material");
        assertTrue(CreatedItems.validate(discovered, netherite, "iron", "gear").isEmpty(), "name must match the material");

        // A gem is the ingot of non-metals: no ingot, no wire offered for it.
        var gems = Map.of(ResourceLocation.parse("c:gems/fluxite"), List.of(ResourceLocation.parse("oritech:fluxite")));
        var gemDiscovered = TagDiscovery.discover(gems);
        var fluxite = CanonicalResolver.resolve(gemDiscovered, ResolutionPolicy.NONE).get(new MaterialId("fluxite"));
        assertTrue(CreatedItems.validate(gemDiscovered, fluxite, "fluxite", "ingot").isEmpty());
        assertTrue(CreatedItems.validate(gemDiscovered, fluxite, "fluxite", "wire").isEmpty());
        assertTrue(CreatedItems.validate(gemDiscovered, fluxite, "fluxite", "plate").isPresent());
    }

    /** MNX-073: a pack can give a created item its own texture; adding entries from the GUI keeps it. */
    @Test
    void ownTextureIsReadAndKept(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws java.io.IOException {
        var file = dir.resolve("items.json");
        java.nio.file.Files.writeString(file, "{\"items\":[{\"material\":\"netherite\",\"form\":\"rod\",\"texture\":\"mypack:item/netherite_rod\"}]}");
        CreatedItems.add(file, List.of());
        var rod = CreatedItems.load(file).getFirst();
        assertEquals(ResourceLocation.parse("mypack:item/netherite_rod"), rod.texture().orElseThrow());
    }
}
