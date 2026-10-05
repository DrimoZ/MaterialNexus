package dev.drimoz.materialnexus.core.recipe;

import net.minecraft.resources.ResourceLocation;

public record RecipeChange(ResourceLocation recipeId, Action action, String reason) {
    public enum Action { KEEP, DISABLE, GENERATE, MODIFY }
}
