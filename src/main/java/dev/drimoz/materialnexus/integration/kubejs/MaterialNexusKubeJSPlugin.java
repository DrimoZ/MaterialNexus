package dev.drimoz.materialnexus.integration.kubejs;

import com.google.gson.JsonElement;
import dev.drimoz.materialnexus.integration.ScriptSources;
import dev.latvian.mods.kubejs.core.RecipeManagerKJS;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * KubeJS plugin (MNX-067), listed in {@code kubejs.plugins.txt}: KubeJS loads it only when installed, so this package is
 * the only one referencing KubeJS. Scripts get {@code MaterialNexus} ({@link MaterialNexusScripts}); their recipe edits
 * are read back for Material Nexus (MNX-076, {@link KubeRecipeEdits}).
 */
public final class MaterialNexusKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MaterialNexus", MaterialNexusScripts.class);
    }

    /** MNX-076: keeps the event to read what scripts changed once the reload is done (read-only, ADR-022). */
    @Override
    public void beforeRecipeLoading(RecipesKubeEvent event, RecipeManagerKJS manager, Map<ResourceLocation, JsonElement> recipes) {
        ScriptSources.recipes(() -> KubeRecipeEdits.read(event));
    }
}
