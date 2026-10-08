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
        scripts(out, snapshot);
        return out.toString();
    }

    /** MNX-076: what scripts decide, what they changed in tags, and the recipes they created (docs/20). */
    private static void scripts(StringBuilder out, ResolvedSnapshot snapshot) {
        var changes = snapshot.scripts();
        if (snapshot.scriptDecisions().isEmpty() && changes.tags().isEmpty() && changes.recipes().isEmpty()) return;
        out.append("## Script changes\n\n")
                .append("Read at the last data load (tag entries changed in memory; with KubeJS, recipes changed, removed or created). ")
                .append("A decision is a proposal: keep it in the Scripts view, apply, then the script lines can go.\n\n");
        if (!snapshot.scriptDecisions().isEmpty()) {
            out.append("| Form | Scripts keep | Material Nexus keeps | Status | Seen in | Evidence |\n|---|---|---|---|---|---|\n");
            for (var d : snapshot.scriptDecisions()) {
                var current = snapshot.form(d.key());
                out.append("| ").append(d.key())
                        .append(" | ").append(d.kept().map(ResourceLocation::toString).orElse("-"))
                        .append(" | ").append(current.flatMap(ResolvedForm::canonical).map(ResourceLocation::toString).orElse("-"))
                        .append(" | ").append(dev.drimoz.materialnexus.core.scripts.ScriptDecisions.status(d, current).name().toLowerCase())
                        .append(" | ").append(d.seenIn().isEmpty() ? "-" : String.join(", ", d.seenIn()))
                        .append(" | ").append(d.evidence().stream().map(e -> e.kind() + " " + e.where() + ": " + e.from() + e.to().map(t -> " -> " + t).orElse(""))
                                .collect(Collectors.joining("; ")))
                        .append(" |\n");
            }
            out.append('\n');
        }
        // Removals are evidence and listed; additions are only counted: mods such as GregTech add thousands in memory.
        var removed = changes.tags().stream().filter(t -> !t.added()).toList();
        if (!removed.isEmpty()) {
            out.append("Tag entries removed in memory: ").append(removed.stream()
                    .map(t -> t.item() + " from #" + t.tag()).collect(Collectors.joining(", "))).append("\n\n");
        }
        var addedByMod = changes.tags().stream().filter(dev.drimoz.materialnexus.core.scripts.ScriptChanges.TagEdit::added)
                .collect(Collectors.groupingBy(t -> t.item().getNamespace(), java.util.TreeMap::new, Collectors.counting()));
        if (!addedByMod.isEmpty()) {
            out.append("Tag entries added in memory: ").append(addedByMod.values().stream().mapToLong(Long::longValue).sum()).append(" (items of ")
                    .append(addedByMod.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "))).append(").\n\n");
        }
        var added = changes.addedBySource();
        if (!added.isEmpty()) {
            out.append("Recipes created by scripts (not unification; they stay in the scripts):\n\n");
            added.forEach((source, ids) -> out.append("- `").append(source).append("`: ")
                    .append(ids.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "))).append('\n'));
            out.append('\n');
        }
    }

    private static String args(ResolvedForm f) {
        return args(f.reasonArgs());
    }

    private static String args(List<String> args) {
        List<String> shown = args.stream().filter(a -> !a.isEmpty()).toList();
        return shown.isEmpty() ? "" : " " + String.join(" ", shown);
    }
}
