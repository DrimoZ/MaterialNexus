package dev.drimoz.materialnexus.core.scripts;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What scripts changed in the data of one load (MNX-076, docs/20), as ids only: item tag entries added or removed in
 * memory (files vs final tags), and recipe edits read from KubeJS. Immutable; built at each data load, never stored.
 */
public record ScriptChanges(List<TagEdit> tags, List<RecipeEdit> recipes) {
    public static final ScriptChanges NONE = new ScriptChanges(List.of(), List.of());

    /** {@code item} added to (or removed from) {@code tag} after the data files were read. */
    public record TagEdit(Identifier tag, Identifier item, boolean added) { }

    public enum Kind { ADDED, REMOVED, CHANGED }

    /**
     * A recipe a script added, removed or changed. {@code gone}: ids that left it (for a removed recipe, its outputs);
     * {@code came}: ids that came in (for an added recipe, its outputs). {@code source}: script file and line, when known.
     */
    public record RecipeEdit(Identifier recipe, Kind kind, Set<Identifier> gone, Set<Identifier> came, Optional<String> source) {
        public RecipeEdit {
            gone = Set.copyOf(gone);
            came = Set.copyOf(came);
        }
    }

    public ScriptChanges {
        tags = List.copyOf(tags);
        recipes = List.copyOf(recipes);
    }

    /** Tag entries present in only one of the two views, sorted by tag then item. */
    public static List<TagEdit> tagEdits(Map<Identifier, ? extends Collection<Identifier>> files,
                                         Map<Identifier, ? extends Collection<Identifier>> loaded) {
        Set<Identifier> tags = new TreeSet<>(files.keySet());
        tags.addAll(loaded.keySet());
        List<TagEdit> edits = new java.util.ArrayList<>();
        for (Identifier tag : tags) {
            Set<Identifier> before = new TreeSet<Identifier>(files.containsKey(tag) ? files.get(tag) : List.of());
            Set<Identifier> after = new TreeSet<Identifier>(loaded.containsKey(tag) ? loaded.get(tag) : List.of());
            for (Identifier item : before) if (!after.contains(item)) edits.add(new TagEdit(tag, item, false));
            for (Identifier item : after) if (!before.contains(item)) edits.add(new TagEdit(tag, item, true));
        }
        return edits;
    }

    /** Recipes created by scripts, by script source ("?" when unknown). */
    public Map<String, List<Identifier>> addedBySource() {
        Map<String, List<Identifier>> out = new TreeMap<>();
        recipes.stream().filter(r -> r.kind() == Kind.ADDED)
                .forEach(r -> out.computeIfAbsent(r.source().orElse("?"), s -> new java.util.ArrayList<>()).add(r.recipe()));
        return out;
    }
}
