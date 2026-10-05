package dev.drimoz.materialnexus.core.recipe;

import dev.drimoz.materialnexus.core.domain.RecipeClassification;
import net.minecraft.resources.ResourceLocation;

public record RecipeAnalysis(ResourceLocation recipeId, RecipeClassification classification, String explanation) { }
