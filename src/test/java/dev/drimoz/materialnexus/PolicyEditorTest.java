package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.CanonicalResolver;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.datapack.PolicyFiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Apply writes the pack author's files: validate first, never duplicate a material, keep what was there. */
class PolicyEditorTest {
    private static final ResourceLocation MEK = ResourceLocation.fromNamespaceAndPath("mekanism", "ingot_tin");
    private static final ResourceLocation TE = ResourceLocation.fromNamespaceAndPath("thermal", "tin_ingot");

    @Test
    void applyRoundTripsThroughThePolicyFiles(@TempDir Path root) throws IOException {
        var discovered = TagDiscovery.discover(Map.of(ResourceLocation.fromNamespaceAndPath("c", "ingots/tin"), List.of(MEK, TE)));
        var snapshot = new ResolvedSnapshot(1, Instant.EPOCH, discovered, CanonicalResolver.resolve(discovered, ResolutionPolicy.NONE));

        Path policies = root.resolve("policies");
        Path existing = policies.resolve("materials").resolve("my_tin_rules.json");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "{\"material\":\"tin\",\"mod_priority\":[\"thermal\"],\"note\":\"keep me\"}");

        List<PolicyEditor.Entry> entries = PolicyEditor.preview(snapshot, List.of(
                new CanonicalChange("tin", "ingot", MEK),
                new CanonicalChange("tin", "plate", ResourceLocation.fromNamespaceAndPath("ghost", "tin_plate"))));
        assertTrue(entries.stream().filter(e -> e.form().equals("ingot")).allMatch(PolicyEditor.Entry::valid));
        assertTrue(entries.stream().filter(e -> e.form().equals("plate")).noneMatch(PolicyEditor.Entry::valid));

        PolicyEditor.apply(policies, entries);

        try (var files = Files.list(policies.resolve("materials"))) {
            assertEquals(1, files.count(), "the existing file for tin must be edited, not duplicated");
        }
        String written = Files.readString(existing);
        assertTrue(written.contains("keep me") && !written.contains("ghost"), written);
        assertTrue(Files.exists(PolicyEditor.backupDir(policies).resolve("materials").resolve("my_tin_rules.json")));

        ResolutionPolicy reloaded = PolicyFiles.load(policies);
        MaterialForm tinIngot = new MaterialForm(new MaterialId("tin"), new FormId("ingot"));
        assertEquals(MEK, reloaded.explicitProviders().get(tinIngot));
        assertEquals(MEK, CanonicalResolver.resolve(discovered, reloaded).get(new MaterialId("tin")).forms().get(new FormId("ingot")).canonical().orElseThrow());

        // MNX-023: revert restores the exact previous file; reverting again redoes the apply.
        PolicyEditor.revert(policies);
        assertEquals("{\"material\":\"tin\",\"mod_priority\":[\"thermal\"],\"note\":\"keep me\"}", Files.readString(existing));
        PolicyEditor.revert(policies);
        assertEquals(MEK, PolicyFiles.load(policies).explicitProviders().get(tinIngot));

        // MNX-058: back to default removes only the saved choice; valid only where a choice is saved.
        var saved = new ResolvedSnapshot(2, Instant.EPOCH, discovered, CanonicalResolver.resolve(discovered, PolicyFiles.load(policies)));
        List<PolicyEditor.Entry> resets = PolicyEditor.preview(saved, List.of(
                new CanonicalChange("tin", "ingot", CanonicalChange.RESET), new CanonicalChange("tin", "plate", CanonicalChange.RESET)));
        assertEquals(List.of(true, false), resets.stream().map(PolicyEditor.Entry::valid).toList());
        PolicyEditor.apply(policies, resets);
        assertNull(PolicyFiles.load(policies).explicitProviders().get(tinIngot));
        assertTrue(Files.readString(existing).contains("keep me"));

        // MNX-060: a written choice the resolver ignores (item no longer in the form) can be reset too.
        Files.writeString(existing, "{\"material\":\"tin\",\"forms\":{\"ingot\":{\"preferred_provider\":\"ghost:tin_ingot\"}}}");
        var ignored = PolicyEditor.preview(saved, List.of(new CanonicalChange("tin", "ingot", CanonicalChange.RESET)),
                PolicyFiles.load(policies).explicitProviders().keySet());
        assertTrue(ignored.getFirst().valid());
        PolicyEditor.apply(policies, ignored);
        assertTrue(PolicyFiles.load(policies).explicitProviders().isEmpty());
    }
}
