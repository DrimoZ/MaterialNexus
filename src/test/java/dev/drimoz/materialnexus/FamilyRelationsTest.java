package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations.Edge;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations.Relation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ADR-003: only report conversions that exist nowhere, and only between forms the material actually has. */
class FamilyRelationsTest {
    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("modx", path);
    }

    @Test
    void reportsOnlyAbsentConversionsBetweenExistingForms() {
        var forms = Map.of(
                new FormId("ingot"), Set.of(rl("tin_ingot"), rl("other_tin_ingot")),
                new FormId("nugget"), Set.of(rl("tin_nugget")),
                new FormId("block"), Set.of(rl("tin_block")));
        var recipes = List.of(
                // 9 nuggets -> ingot exists, through an ingredient accepting any tin nugget.
                new Edge(rl("tin_ingot"), List.of(Set.of(rl("tin_nugget")))),
                // ingot -> block exists via the alternative ingot: still counts.
                new Edge(rl("tin_block"), List.of(Set.of(rl("other_tin_ingot")))));

        // Missing: ingot -> nugget and block -> ingot. No raw/dust forms here, so nothing is asked about them.
        assertEquals(List.of(new Relation(new FormId("ingot"), new FormId("nugget")), new Relation(new FormId("block"), new FormId("ingot"))),
                FamilyRelations.missing(forms, recipes));
    }
}
