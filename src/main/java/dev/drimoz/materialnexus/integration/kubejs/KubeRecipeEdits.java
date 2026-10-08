package dev.drimoz.materialnexus.integration.kubejs;

import com.google.gson.JsonObject;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges;
import dev.drimoz.materialnexus.core.scripts.ScriptChanges.Kind;
import dev.drimoz.materialnexus.datapack.RecipeSources;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The recipe edits of one KubeJS recipe event, as ids (MNX-076, docs/20). Read once, after the event was posted and its
 * changes serialized; the event is not kept. Fields read are public, checked against KubeJS 2001.6.5 (which records no
 * script line, unlike 2101 on main).
 */
final class KubeRecipeEdits {
    /** Keys holding a recipe's outputs in vanilla and most modded formats. */
    private static final List<String> OUTPUT_KEYS = List.of("result", "results", "output", "outputs");

    private KubeRecipeEdits() { }

    static List<ScriptChanges.RecipeEdit> read(RecipesEventJS event) {
        List<ScriptChanges.RecipeEdit> edits = new ArrayList<>();
        for (RecipeJS r : event.originalRecipes.values()) {
            if (r.removed) {
                edits.add(new ScriptChanges.RecipeEdit(r.getOrCreateId(), Kind.REMOVED, outputs(r.originalJson), Set.of(), Optional.empty()));
            } else if (r.hasChanged() && r.json != null && r.originalJson != null) {
                Set<ResourceLocation> before = RecipeSources.ids(r.originalJson.toString());
                Set<ResourceLocation> after = RecipeSources.ids(r.json.toString());
                Set<ResourceLocation> gone = new HashSet<>(before);
                gone.removeAll(after);
                Set<ResourceLocation> came = new HashSet<>(after);
                came.removeAll(before);
                if (!gone.isEmpty() || !came.isEmpty()) edits.add(new ScriptChanges.RecipeEdit(r.getOrCreateId(), Kind.CHANGED, gone, came, Optional.empty()));
            }
        }
        for (RecipeJS r : event.addedRecipes) {
            if (r.removed) continue;
            edits.add(new ScriptChanges.RecipeEdit(r.getOrCreateId(), Kind.ADDED, Set.of(), outputs(r.json), Optional.empty()));
        }
        return edits;
    }

    private static Set<ResourceLocation> outputs(JsonObject json) {
        Set<ResourceLocation> out = new HashSet<>();
        if (json == null) return out;
        for (String key : OUTPUT_KEYS) {
            if (json.has(key)) out.addAll(RecipeSources.ids(json.get(key).toString()));
        }
        return out;
    }
}
