package dev.drimoz.materialnexus.client.compat.jei;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.client.RecipeViewer;
import dev.drimoz.materialnexus.client.UnifiedItemsClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JEI adapter (MNX-013): hides the alternatives of the applied unification and shows them again if a
 * revert brings them back. Only JEI instantiates this class, so nothing here loads when JEI is absent.
 */
@JeiPlugin
public final class MnxJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "jei");

    private IJeiRuntime runtime;
    /** What this runtime currently hides; a new runtime (JEI restarts on reload) starts with nothing hidden. */
    private final Set<ResourceLocation> hidden = new HashSet<>();
    private boolean subscribed;
    private Viewer viewer;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        hidden.clear();
        // MNX-088: the Recipes tab asks this adapter to draw recipes the way JEI does.
        viewer = new Viewer(jeiRuntime);
        RecipeViewer.Slot.current = viewer;
        if (!subscribed) {
            UnifiedItemsClient.subscribe(this::apply);
            subscribed = true;
        }
        apply(UnifiedItemsClient.alternatives());
    }

    @Override
    public void onRuntimeUnavailable() {
        if (RecipeViewer.Slot.current == viewer) RecipeViewer.Slot.current = null;
        viewer = null;
        runtime = null;
        hidden.clear();
    }

    private void apply(Set<ResourceLocation> alternatives) {
        if (runtime == null) return;
        Set<ResourceLocation> show = new HashSet<>(hidden);
        show.removeAll(alternatives);
        Set<ResourceLocation> hide = new HashSet<>(alternatives);
        hide.removeAll(hidden);
        if (!show.isEmpty()) runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks(show));
        if (!hide.isEmpty()) runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks(hide));
        hidden.removeAll(show);
        hidden.addAll(hide);
    }

    private static List<ItemStack> stacks(Set<ResourceLocation> ids) {
        return ids.stream().flatMap(id -> BuiltInRegistries.ITEM.getOptional(id).stream()).map(ItemStack::new).toList();
    }

    /** JEI as the drawer of recipes (MNX-088): the layout of a loaded recipe, found in the category that holds it. */
    private static final class Viewer implements RecipeViewer {
        private final IJeiRuntime runtime;
        private final java.util.Map<ResourceLocation, java.util.Optional<Layout>> cache = new java.util.HashMap<>();

        Viewer(IJeiRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public String name() { return "JEI"; }

        @Override
        public java.util.Optional<Layout> layout(ResourceLocation recipe) {
            return cache.computeIfAbsent(recipe, this::find);
        }

        private java.util.Optional<Layout> find(ResourceLocation id) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return java.util.Optional.empty();
            var holder = level.getRecipeManager().byKey(id);
            if (holder.isEmpty()) return java.util.Optional.empty();
            var focus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
            for (mezz.jei.api.recipe.category.IRecipeCategory<?> category : runtime.getRecipeManager().createRecipeCategoryLookup().get().toList()) {
                java.util.Optional<Layout> layout = in(category, holder.get(), focus);
                if (layout.isPresent()) return layout;
            }
            return java.util.Optional.empty();
        }

        /** A category takes the recipe holder or the recipe itself; the recipe must be one it has registered. */
        private <T> java.util.Optional<Layout> in(mezz.jei.api.recipe.category.IRecipeCategory<T> category, net.minecraft.world.item.crafting.RecipeHolder<?> holder,
                                                  mezz.jei.api.recipe.IFocusGroup focus) {
            try {
                Class<? extends T> type = category.getRecipeType().getRecipeClass();
                Object candidate = type.isInstance(holder) ? holder : type.isInstance(holder.value()) ? holder.value() : null;
                if (candidate == null) return java.util.Optional.empty();
                T recipe = type.cast(candidate);
                var manager = runtime.getRecipeManager();
                if (manager.createRecipeLookup(category.getRecipeType()).get().noneMatch(r -> r == recipe || r.equals(recipe))) return java.util.Optional.empty();
                return manager.createRecipeLayoutDrawable(category, recipe, focus).map(drawable -> new Layout() {
                    @Override public int width() { return drawable.getRect().getWidth(); }
                    @Override public int height() { return drawable.getRect().getHeight(); }
                    @Override public net.minecraft.network.chat.Component title() { return category.getTitle(); }

                    @Override
                    public void draw(net.minecraft.client.gui.GuiGraphics g, int x, int y, int mouseX, int mouseY) {
                        drawable.setPosition(x, y);
                        drawable.drawRecipe(g, mouseX, mouseY);
                        drawable.drawOverlays(g, mouseX, mouseY);
                    }
                });
            } catch (RuntimeException e) {
                // A category that cannot lay this recipe out is simply not the one.
                return java.util.Optional.empty();
            }
        }

        @Override
        public void open(ResourceLocation item) {
            BuiltInRegistries.ITEM.getOptional(item).ifPresent(i -> runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                    .createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(i))));
        }
    }
}
