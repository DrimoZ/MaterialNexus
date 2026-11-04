package dev.drimoz.materialnexus;

import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges.Kind;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges.RecipeEdit;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** MNX-076: what scripts keep is deduced only from clear evidence; anything ambiguous stays a non-decision. */
class ScriptDecisionsTest {
    private static Identifier id(String s) { return Identifier.parse(s); }

    private static final Identifier INGOTS = id("c:ingots/tin");
    private static final Identifier NUGGETS = id("c:nuggets/tin");
    private static final Identifier DUSTS = id("c:dusts/tin");
    private static final Identifier A_INGOT = id("moda:tin_ingot");
    private static final Identifier B_INGOT = id("modb:tin_ingot");
    private static final Identifier C_INGOT = id("modc:tin_ingot");
    private static final Identifier A_NUGGET = id("moda:tin_nugget");
    private static final Identifier B_NUGGET = id("modb:tin_nugget");
    private static final Identifier A_DUST = id("moda:tin_dust");
    private static final Identifier B_DUST = id("modb:tin_dust");

    private static final Map<Identifier, List<Identifier>> FILES = Map.of(
            INGOTS, List.of(A_INGOT, B_INGOT, C_INGOT), NUGGETS, List.of(A_NUGGET, B_NUGGET), DUSTS, List.of(A_DUST, B_DUST));

    private static RecipeEdit changed(String recipe, Identifier gone, Identifier came) {
        return new RecipeEdit(id(recipe), Kind.CHANGED, Set.of(gone), Set.of(came), Optional.empty());
    }

    private static Map<String, Optional<Identifier>> kept(List<ScriptChanges.TagEdit> tags, List<RecipeEdit> recipes) {
        Map<String, Optional<Identifier>> out = new java.util.TreeMap<>();
        ScriptDecisions.infer(TagDiscovery.discover(FILES), new ScriptChanges(tags, recipes)).forEach(d -> out.put(d.key().toString(), d.kept()));
        return out;
    }

    @Test
    void replacementsAndTagRemovalsDecide() {
        // A replaced by B in a smelting recipe; a nugget turned into an ingot says nothing about either group.
        var recipes = List.of(changed("moda:smelting/tin", A_INGOT, B_INGOT), changed("moda:craft/x", A_NUGGET, B_INGOT));
        // A's dust removed from the tag leaves only B's.
        var tags = ScriptChanges.tagEdits(FILES, Map.of(INGOTS, List.of(A_INGOT, B_INGOT, C_INGOT), DUSTS, List.of(B_DUST),
                NUGGETS, List.of(A_NUGGET, B_NUGGET)));
        assertEquals(List.of(new ScriptChanges.TagEdit(DUSTS, A_DUST, false)), tags);

        var kept = kept(tags, recipes);
        assertEquals(Optional.of(B_INGOT), kept.get("tin/ingot"));
        assertEquals(Optional.of(B_DUST), kept.get("tin/dust"));
        assertFalse(kept.containsKey("tin/nugget"), "a replacement across groups is not evidence");
    }

    @Test
    void decisionsArePlacedOnTheScriptLinesNamingTheItemSetAside() {
        var decisions = ScriptDecisions.infer(TagDiscovery.discover(FILES), new ScriptChanges(List.of(), List.of(changed("moda:smelting/tin", A_INGOT, B_INGOT))));
        var scripts = Map.of(
                "unify.js", List.of("// moda:tin_ingot is replaced below: 'moda:tin_ingot'",
                        "event.replaceOutput({}, 'moda:tin_ingot', 'modb:tin_ingot')",
                        "event.remove({ output: 'moda:tin_ingot_block' })"),
                "metals/loop.js", List.of("metals.forEach(m => event.replaceOutput({}, `moda:${m}_ingot`, `modb:${m}_ingot`))"));
        // The comment, a longer id and an id built in code are not the decision's line.
        assertEquals(List.of("unify.js:2"), ScriptDecisions.locate(decisions, scripts).getFirst().seenIn());
    }

    @Test
    void ambiguousScriptsDecideNothing() {
        // A replaced by B in one recipe and by C in another: the scripts disagree.
        var disagree = kept(List.of(), List.of(changed("moda:r1", A_INGOT, B_INGOT), changed("moda:r2", A_INGOT, C_INGOT)));
        assertEquals(Optional.empty(), disagree.get("tin/ingot"));

        // A's ingot set aside, two left: nothing kept, but listed.
        var aside = kept(List.of(new ScriptChanges.TagEdit(INGOTS, A_INGOT, false)), List.of());
        assertEquals(Optional.empty(), aside.get("tin/ingot"));

        // Replaced by B but B's recipes removed elsewhere: never kept.
        var removed = new RecipeEdit(id("modb:smelting/tin"), Kind.REMOVED, Set.of(B_INGOT), Set.of(), Optional.empty());
        assertEquals(Optional.empty(), kept(List.of(), List.of(changed("moda:r1", A_INGOT, B_INGOT), removed)).get("tin/ingot"));
    }
}
