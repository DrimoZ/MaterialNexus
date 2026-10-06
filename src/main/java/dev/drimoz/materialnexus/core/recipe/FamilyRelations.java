package dev.drimoz.materialnexus.core.recipe;

import dev.drimoz.materialnexus.core.domain.FormId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Missing recipe detection (MNX-010, ADR-003): which standard conversions between the forms of one
 * material have no recipe at all. The result is a list of proposals to show; nothing is ever created
 * from it automatically, because a form existing does not prove a conversion is wanted or balanced.
 */
public final class FamilyRelations {
    /** A conversion between two forms of the same material. */
    public record Relation(FormId from, FormId to) { }

    /** A loaded recipe reduced to what matters here: the item it makes and the items each ingredient accepts. */
    public record Edge(ResourceLocation result, List<Set<ResourceLocation>> ingredients) { }

    /** The conversions a material with both forms is normally expected to have. */
    public static final List<Relation> EXPECTED = List.of(
            relation("nugget", "ingot"), relation("ingot", "nugget"),
            relation("ingot", "block"), relation("block", "ingot"),
            relation("raw", "raw_block"), relation("raw_block", "raw"),
            relation("raw", "ingot"), relation("dust", "ingot"));

    /** Longest chain of recipes still counted as a conversion (dust, hot ingot, ingot is two). */
    public static final int MAX_STEPS = 3;

    private FamilyRelations() { }

    private static Relation relation(String from, String to) {
        return new Relation(new FormId(from), new FormId(to));
    }

    /** Expected relations whose two forms exist for the material but that no recipe provides. */
    public static List<Relation> missing(Map<FormId, Set<ResourceLocation>> providersByForm, List<Edge> recipes) {
        List<Relation> missing = new ArrayList<>();
        Map<ResourceLocation, Set<ResourceLocation>> graph = graph(recipes);
        for (Relation r : EXPECTED) {
            Set<ResourceLocation> from = providersByForm.get(r.from());
            Set<ResourceLocation> to = providersByForm.get(r.to());
            if (from == null || to == null || from.isEmpty() || to.isEmpty()) continue;
            if (!reachable(from, to, graph)) missing.add(r);
        }
        return missing;
    }

    /** Item to the items recipes make from it. */
    private static Map<ResourceLocation, Set<ResourceLocation>> graph(List<Edge> recipes) {
        Map<ResourceLocation, Set<ResourceLocation>> graph = new java.util.HashMap<>();
        for (Edge e : recipes) {
            for (Set<ResourceLocation> accepted : e.ingredients()) {
                for (ResourceLocation input : accepted) graph.computeIfAbsent(input, k -> new java.util.HashSet<>()).add(e.result());
            }
        }
        return graph;
    }

    /**
     * Whether some item of {@code to} can be made from some item of {@code from} in at most {@link #MAX_STEPS}
     * recipes, so chains through intermediates (MI: dust, hot ingot, ingot) count as present.
     */
    private static boolean reachable(Set<ResourceLocation> from, Set<ResourceLocation> to, Map<ResourceLocation, Set<ResourceLocation>> graph) {
        Set<ResourceLocation> frontier = new java.util.HashSet<>(from);
        Set<ResourceLocation> seen = new java.util.HashSet<>(from);
        for (int step = 0; step < MAX_STEPS && !frontier.isEmpty(); step++) {
            Set<ResourceLocation> next = new java.util.HashSet<>();
            for (ResourceLocation item : frontier) {
                for (ResourceLocation made : graph.getOrDefault(item, Set.of())) {
                    if (to.contains(made)) return true;
                    if (seen.add(made)) next.add(made);
                }
            }
            frontier = next;
        }
        return false;
    }
}
