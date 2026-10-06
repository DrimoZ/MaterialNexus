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

    private FamilyRelations() { }

    private static Relation relation(String from, String to) {
        return new Relation(new FormId(from), new FormId(to));
    }

    /** Expected relations whose two forms exist for the material but that no recipe provides. */
    public static List<Relation> missing(Map<FormId, Set<ResourceLocation>> providersByForm, List<Edge> recipes) {
        List<Relation> missing = new ArrayList<>();
        for (Relation r : EXPECTED) {
            Set<ResourceLocation> from = providersByForm.get(r.from());
            Set<ResourceLocation> to = providersByForm.get(r.to());
            if (from == null || to == null || from.isEmpty() || to.isEmpty()) continue;
            boolean present = recipes.stream().anyMatch(e -> to.contains(e.result())
                    && e.ingredients().stream().anyMatch(accepted -> accepted.stream().anyMatch(from::contains)));
            if (!present) missing.add(r);
        }
        return missing;
    }
}
