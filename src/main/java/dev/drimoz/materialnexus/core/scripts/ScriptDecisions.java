package dev.drimoz.materialnexus.core.scripts;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.Provider;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which item scripts keep, per material/form (MNX-076, docs/20). Pure: groups come from discovery on the tags as the
 * data files define them, so an item a script removed from its tag still belongs to its group. Proposals only
 * (ADR-022): nothing here changes the policy.
 */
public final class ScriptDecisions {
    /** Evidence kinds, also the lang key suffixes ({@code screen.materialnexus.scripts.evidence.<kind>}). */
    public static final String REPLACED = "replaced";
    public static final String TAG_REMOVED = "tag_removed";
    public static final String RECIPE_REMOVED = "recipe_removed";

    /** {@code where}: the recipe or tag; {@code from}: the item set aside; {@code to}: the item put in its place. */
    public record Evidence(String kind, ResourceLocation where, ResourceLocation from, Optional<ResourceLocation> to) { }

    /**
     * {@code kept} is empty when scripts only set items aside, or disagree. {@code seenIn}: script lines naming an item
     * set aside ("unify.js:12"), found by text search (MNX-077); empty when ids are built in code.
     */
    public record Decision(MaterialForm key, Optional<ResourceLocation> kept, List<ResourceLocation> setAside, List<Evidence> evidence,
                           List<String> seenIn) {
        public Decision {
            setAside = List.copyOf(setAside);
            evidence = List.copyOf(evidence);
            seenIn = List.copyOf(seenIn);
        }

        public Decision(MaterialForm key, Optional<ResourceLocation> kept, List<ResourceLocation> setAside, List<Evidence> evidence) {
            this(key, kept, setAside, evidence, List.of());
        }
    }

    /** Lines kept per decision: enough to find the script part, not a listing of it. */
    static final int MAX_SEEN_IN = 5;

    /**
     * MNX-077: where each decision is written, as far as a text search can tell. KubeJS records no source line for
     * replacements, removals or tag edits, so the script files ({@code path -> lines}) are searched for the quoted ids
     * of the items set aside; comment lines are skipped. An id built in code is not found, and nothing is guessed.
     */
    public static List<Decision> locate(List<Decision> decisions, java.util.Map<String, List<String>> scripts) {
        if (scripts.isEmpty()) return decisions;
        return decisions.stream().map(d -> {
            List<String> seen = new ArrayList<>();
            new java.util.TreeMap<>(scripts).forEach((file, lines) -> {
                for (int i = 0; i < lines.size() && seen.size() < MAX_SEEN_IN; i++) {
                    String line = lines.get(i).strip();
                    if (line.startsWith("//") || line.startsWith("*")) continue;
                    if (d.setAside().stream().anyMatch(id -> line.contains("'" + id + "'") || line.contains("\"" + id + "\"") || line.contains("`" + id + "`"))) {
                        seen.add(file + ":" + (i + 1));
                    }
                }
            });
            return new Decision(d.key(), d.kept(), d.setAside(), d.evidence(), seen);
        }).toList();
    }

    /** Against what Material Nexus keeps now. */
    public enum Status { RECORDED, SAME, DIFFERS, UNDECIDED }

    private ScriptDecisions() { }

    public static List<Decision> infer(DiscoveredMaterials files, ScriptChanges changes) {
        List<Decision> decisions = new ArrayList<>();
        files.materials().forEach((material, forms) -> forms.forEach((form, providers) -> {
            if (providers.size() < 2) return;
            MaterialForm key = new MaterialForm(material, form);
            Set<ResourceLocation> members = new TreeSet<>(providers.stream().map(Provider::resource).toList());
            Set<ResourceLocation> wanted = new TreeSet<>();
            Set<ResourceLocation> against = new TreeSet<>();
            List<Evidence> evidence = new ArrayList<>();

            for (ScriptChanges.RecipeEdit r : changes.recipes()) {
                if (r.kind() == ScriptChanges.Kind.CHANGED) {
                    List<ResourceLocation> in = r.came().stream().filter(members::contains).sorted().toList();
                    for (ResourceLocation out : r.gone().stream().filter(members::contains).sorted().toList()) {
                        // Both ends in the same group: X replaced by Y. One end only says nothing about this group.
                        for (ResourceLocation to : in) {
                            wanted.add(to);
                            against.add(out);
                            evidence.add(new Evidence(REPLACED, r.recipe(), out, Optional.of(to)));
                        }
                    }
                } else if (r.kind() == ScriptChanges.Kind.REMOVED) {
                    r.gone().stream().filter(members::contains).sorted().forEach(out -> {
                        against.add(out);
                        evidence.add(new Evidence(RECIPE_REMOVED, r.recipe(), out, Optional.empty()));
                    });
                }
            }
            Optional<ResourceLocation> tag = TagDiscovery.conventionTag(material, form);
            for (ScriptChanges.TagEdit t : changes.tags()) {
                if (!t.added() && tag.isPresent() && t.tag().equals(tag.get()) && members.contains(t.item())) {
                    against.add(t.item());
                    evidence.add(new Evidence(TAG_REMOVED, t.tag(), t.item(), Optional.empty()));
                }
            }
            if (evidence.isEmpty()) return;

            Set<ResourceLocation> keep = new TreeSet<>(wanted.isEmpty() ? members : wanted);
            keep.removeAll(against);
            // With no replacement, setting every item aside but one keeps that one; otherwise one clear winner only.
            boolean decided = keep.size() == 1;
            decisions.add(new Decision(key, decided ? Optional.of(keep.iterator().next()) : Optional.empty(), List.copyOf(against), evidence));
        }));
        return decisions;
    }

    public static Status status(Decision decision, Optional<ResolvedForm> current) {
        if (decision.kept().isEmpty()) return Status.UNDECIDED;
        Optional<ResourceLocation> canonical = current.flatMap(ResolvedForm::canonical);
        if (!canonical.equals(decision.kept())) return Status.DIFFERS;
        return current.get().source() == PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE ? Status.RECORDED : Status.SAME;
    }
}
