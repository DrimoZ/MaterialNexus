package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

import java.util.Optional;

/**
 * MNX-138: on 26.1 the client has no recipe manager; the recipes a viewer asked the server for arrive with each data sync
 * (option 1 of docs/23). Kept here, free of any viewer class, so the viewer adapters can look a recipe up by id.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipes {
    private static volatile RecipeMap recipes = RecipeMap.EMPTY;

    private ClientRecipes() { }

    @SubscribeEvent
    public static void received(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    /** The recipe as last sent, if a viewer asked for its type. */
    public static Optional<RecipeHolder<?>> byId(Identifier id) {
        return Optional.ofNullable(recipes.byKey(ResourceKey.create(Registries.RECIPE, id)));
    }
}
