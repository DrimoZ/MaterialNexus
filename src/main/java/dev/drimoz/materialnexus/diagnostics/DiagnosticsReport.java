package dev.drimoz.materialnexus.diagnostics;

import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Markdown report of the current analysis, for pack authors to review or share without the GUI
 * (MNX-032). Written on demand by {@code /materials report}; reads the snapshot, changes nothing.
 * Reasons are written as their translation keys: the file is for authors, not players.
 */
public final class DiagnosticsReport {
    private DiagnosticsReport() { }

    public static String build(ResolvedSnapshot snapshot, Map<MaterialId, List<FamilyRelations.Relation>> missing) {
        return build(snapshot, missing, List.of());
    }

    /** With the untagged-form audit (MNX-041): item name shapes to review, and to declare in material_nexus/forms. */
    public static String build(ResolvedSnapshot snapshot, Map<MaterialId, List<FamilyRelations.Relation>> missing,
                               List<dev.drimoz.materialnexus.datapack.FormPatterns.Candidate> untagged) {
        StringBuilder out = new StringBuilder();
        long unified = snapshot.materials().values().stream().flatMap(m -> m.forms().values().stream())
                .filter(f -> f.canonical().isPresent() && !f.alternatives().isEmpty()).count();
        out.append("# Material Nexus report\n\n")
                .append("Snapshot generation ").append(snapshot.generation()).append(", built ").append(snapshot.builtAt()).append(".\n\n")
                .append("- materials: ").append(snapshot.materials().size()).append('\n')
                .append("- providers: ").append(snapshot.discovered().providerCount()).append('\n')
                .append("- forms with interchangeable duplicates: ").append(unified).append("\n\n");

        snapshot.materials().forEach((material, resolved) -> {
            out.append("## ").append(material).append("\n\n")
                    .append("| Form | Canonical | Source | Why | Alternatives | Not unified |\n")
                    .append("|---|---|---|---|---|---|\n");
            resolved.forms().forEach((form, f) -> out.append("| ").append(form)
                    .append(" | ").append(f.canonical().map(ResourceLocation::toString).orElse("-"))
                    .append(" | ").append(f.source().name().toLowerCase())
                    .append(" | ").append(f.reasonKey().replace("materialnexus.why.", "")).append(args(f))
                    .append(" | ").append(f.alternatives().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")))
                    .append(" | ").append(f.notUnified().stream()
                            .map(n -> n.item() + " (" + n.reasonKey().replace("materialnexus.not_unified.", "") + args(n.reasonArgs()) + ")")
                            .collect(Collectors.joining(", ")))
                    .append(" |\n"));
            List<FamilyRelations.Relation> gaps = missing.getOrDefault(material, List.of());
            if (!gaps.isEmpty()) {
                out.append("\nMissing recipes (proposals, never created automatically): ")
                        .append(gaps.stream().map(r -> r.from() + " -> " + r.to()).collect(Collectors.joining(", "))).append('\n');
            }
            out.append('\n');
        });
        if (!untagged.isEmpty()) {
            out.append("## Possibly untagged forms\n\n")
                    .append("Items named after a known material but not discovered, grouped by name shape. A real form can be declared ")
                    .append("in `data/<namespace>/material_nexus/forms/*.json` (`folders` + `patterns`); the rest is not a form (machines, tools...).\n\n")
                    .append("| Pattern | Materials |\n|---|---|\n");
            untagged.forEach(c -> out.append("| `").append(c.pattern()).append("` | ").append(c.materials().size()).append(": ")
                    .append(String.join(", ", c.materials())).append(" |\n"));
            out.append('\n');
        }
        return out.toString();
    }

    private static String args(ResolvedForm f) {
        return args(f.reasonArgs());
    }

    private static String args(List<String> args) {
        List<String> shown = args.stream().filter(a -> !a.isEmpty()).toList();
        return shown.isEmpty() ? "" : " " + String.join(" ", shown);
    }
}
