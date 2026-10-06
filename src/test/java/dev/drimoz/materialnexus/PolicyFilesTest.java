package dev.drimoz.materialnexus;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The two places where a mistake costs the pack author: reading their policy, and writing next to their files. */
class PolicyFilesTest {
    private static final MaterialId COPPER = new MaterialId("copper");

    @Test
    void handWrittenPolicyIsReadAtEveryLevel() {
        ResolutionPolicy policy = PolicyFiles.parse(
                JsonParser.parseString("{\"mod_priority\":[\"minecraft\"],\"almost_unified\":{\"tags\":\"mnx\"}}"),
                Map.of("copper.json", JsonParser.parseString("""
                        {"material":"copper","mod_priority":["mekanism"],
                         "forms":{"plate":{"preferred_provider":"create:copper_sheet"},"wire":{"mod_priority":["immersiveengineering"]}}}""")));

        assertEquals(List.of("minecraft"), policy.globalModPriority());
        assertEquals(List.of("mekanism"), policy.materialModPriority().get(COPPER));
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "copper_sheet"),
                policy.explicitProviders().get(new MaterialForm(COPPER, new FormId("plate"))));
        assertEquals(List.of("immersiveengineering"), policy.formModPriority().get(new MaterialForm(COPPER, new FormId("wire"))));
    }

    @Test
    void ambiguousOrInvalidPolicyFailsWithTheFileNamed() {
        var copper = JsonParser.parseString("{\"material\":\"copper\"}");
        var error = assertThrows(IllegalArgumentException.class,
                () -> PolicyFiles.parse(JsonParser.parseString("{}"), Map.of("a.json", copper, "b.json", copper)));
        assertTrue(error.getMessage().contains("a.json") && error.getMessage().contains("b.json"), error.getMessage());

        var bad = assertThrows(IllegalArgumentException.class, () -> PolicyFiles.parse(JsonParser.parseString("{}"),
                Map.of("tin.json", JsonParser.parseString("{\"material\":\"Tin\"}"))));
        assertTrue(bad.getMessage().contains("tin.json"), bad.getMessage());
    }

    @Test
    void neverOverwritesAFolderItDidNotCreate(@TempDir Path root) throws IOException {
        Path foreign = root.resolve("generated");
        Files.createDirectories(foreign);
        Files.writeString(foreign.resolve("my_notes.txt"), "keep me");

        assertThrows(IOException.class, () -> GeneratedPack.write(foreign, Map.of(), new JsonArray()));
        assertEquals("keep me", Files.readString(foreign.resolve("my_notes.txt")));
    }
}
