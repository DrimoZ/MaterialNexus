package dev.drimoz.materialnexus.integration.kubejs;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import dev.drimoz.materialnexus.core.resolution.SnapshotManager;
import dev.drimoz.materialnexus.datapack.GeneratedPack;
import dev.drimoz.materialnexus.datapack.MnxPaths;
import dev.drimoz.materialnexus.datapack.PackContent;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What KubeJS scripts see as {@code MaterialNexus} (MNX-067, ADR-001: optional bridge). No KubeJS type here: the
 * plugin only binds this class. Read-only: scripts ask, they never change the policy.
 * <ul>
 * <li>{@code kept(item)}, {@code isAlternative(item)}, {@code conversions()}: the applied pack (its manifest on disk),
 * so they are right even in recipe events, which run before Material Nexus reads the new tags.</li>
 * <li>{@code canonical(material, form)}, {@code alternatives(material, form)}: the policy as resolved at the last
 * reload (choices not applied yet included); null / empty before the first one.</li>
 * </ul>
 */
public final class MaterialNexusScripts {
    private static long stamp = Long.MIN_VALUE;
    private static Map<String, String> applied = Map.of();

    private MaterialNexusScripts() { }

    /** Alternative item id to the item kept in its place, as applied; re-read only when the manifest changed. */
    public static synchronized Map<String, String> conversions() {
        Path manifest = MnxPaths.generated().resolve(GeneratedPack.MANIFEST);
        try {
            long modified = Files.exists(manifest) ? Files.getLastModifiedTime(manifest).toMillis() : 0;
            if (modified != stamp) {
                Map<String, String> byId = new TreeMap<>();
                PackContent.itemConversions(PackContent.readManifest(MnxPaths.generated())).forEach((from, to) -> byId.put(from.toString(), to.toString()));
                applied = Map.copyOf(byId);
                stamp = modified;
            }
        } catch (IOException e) {
            // Unreadable manifest: keep what was read before (empty at first).
        }
        return applied;
    }

    /** The item kept in place of {@code item}, or {@code item} itself when it is not an alternative. */
    public static String kept(String item) {
        return conversions().getOrDefault(item, item);
    }

    public static boolean isAlternative(String item) {
        return conversions().containsKey(item);
    }

    /** The item kept for that material and form, or null when there is none (or no snapshot yet). */
    public static String canonical(String material, String form) {
        return resolved(material, form).flatMap(ResolvedForm::canonical).map(ResourceLocation::toString).orElse(null);
    }

    public static List<String> alternatives(String material, String form) {
        return resolved(material, form).map(f -> f.alternatives().stream().map(ResourceLocation::toString).toList()).orElse(List.of());
    }

    private static Optional<ResolvedForm> resolved(String material, String form) {
        Optional<MaterialId> m = MaterialId.read(material).result();
        Optional<FormId> f = FormId.read(form).result();
        if (m.isEmpty() || f.isEmpty()) return Optional.empty();
        return Optional.ofNullable(SnapshotManager.current().materials().get(m.get())).map(ResolvedMaterial::forms).map(forms -> forms.get(f.get()));
    }
}
