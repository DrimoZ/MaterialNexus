package dev.drimoz.materialnexus.datapack;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import dev.drimoz.materialnexus.core.recipe.FamilyRelations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server adapter for {@link FamilyRelations}: the loaded recipes producing one material's items, reduced to
 * edges. Called when the detail of a material is requested, never per tick. Recipes that do not expose
 * their result through vanilla APIs simply do not count as evidence, so a proposal may be conservative.
 */
public final class RecipeEdges {
    private RecipeEdges() { }

    public static List<FamilyRelations.Relation> missing(MinecraftServer server, DiscoveredMaterials discovered, MaterialId material) {
        var forms = discovered.materials().get(material);
        if (forms == null) return List.of();
        Map<FormId, Set<ResourceLocation>> providers = new HashMap<>();
        Set<ResourceLocation> all = new HashSet<>();
        forms.forEach((form, list) -> {
            Set<ResourceLocation> ids = list.stream().map(Provider::resource).collect(Collectors.toSet());
            providers.put(form, ids);
            all.addAll(ids);
        });

        List<FamilyRelations.Edge> edges = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ItemStack out = holder.value().getResultItem(server.registryAccess());
            if (out.isEmpty()) continue;
            ResourceLocation result = BuiltInRegistries.ITEM.getKey(out.getItem());
            if (!all.contains(result)) continue;
            List<Set<ResourceLocation>> ingredients = new ArrayList<>();
            for (Ingredient ingredient : holder.value().getIngredients()) {
                ingredients.add(Arrays.stream(ingredient.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem())).collect(Collectors.toSet()));
            }
            edges.add(new FamilyRelations.Edge(result, ingredients));
        }
        return FamilyRelations.missing(providers, edges);
    }
}
