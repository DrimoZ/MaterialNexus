package dev.drimoz.materialnexus.integration.kubejs;

import dev.drimoz.materialnexus.integration.ScriptSources;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Map;

/**
 * KubeJS plugin (MNX-067), listed in {@code kubejs.plugins.txt}: KubeJS loads it only when installed, so this package is
 * the only one referencing KubeJS. Scripts get {@code MaterialNexus} ({@link MaterialNexusScripts}); their recipe edits
 * are read back for Material Nexus (MNX-076, {@link KubeRecipeEdits}).
 */
public final class MaterialNexusKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("MaterialNexus", MaterialNexusScripts.class);
    }

    /**
     * MNX-076: KubeJS 2001 calls this at the end of its recipe event, scripts done; the event is read once the reload is
     * done (read-only, ADR-022). On main (KubeJS 2101) the hook is {@code beforeRecipeLoading}.
     */
    @Override
    public void injectRuntimeRecipes(RecipesEventJS event, RecipeManager manager, Map<ResourceLocation, Recipe<?>> recipes) {
        ScriptSources.recipes(() -> KubeRecipeEdits.read(event));
    }
}
