package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-019: a preset replaces only the fields it defines, and stays revertable. */
class PresetTest {
    @Test
    void presetOverlaysGlobalPolicyAndCanBeReverted(@TempDir Path root) throws IOException {
        Path policies = root.resolve("policies");
        Files.createDirectories(policies);
        String original = "{\"mod_priority\":[\"create\"],\"almost_unified\":{\"tags\":\"mnx\"}}";
        Files.writeString(policies.resolve("global.json"), original);

        var preset = JsonParser.parseString("{\"mod_priority\":[\"mekanism\",\"minecraft\"],\"conversion_recipes\":[\"ingot\"]}").getAsJsonObject();
        PolicyEditor.apply(policies, List.of(), Optional.of(preset));

        var policy = PolicyFiles.load(policies);
        assertEquals(List.of("mekanism", "minecraft"), policy.globalModPriority());
        assertEquals(1, policy.conversionRecipeForms().size());
        assertFalse(policy.almostUnified().owners().isEmpty(), "fields the preset does not define are kept");

        PolicyEditor.revert(policies);
        assertEquals(original, Files.readString(policies.resolve("global.json")));
    }
}
