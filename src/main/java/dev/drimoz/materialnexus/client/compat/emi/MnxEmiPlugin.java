package dev.drimoz.materialnexus.client.compat.emi;

import dev.drimoz.materialnexus.client.RecipeViewer;
import dev.drimoz.materialnexus.client.UnifiedItemsClient;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EMI adapter (MNX-014): hides the alternatives of the applied unification. EMI calls {@link #register}
 * on each of its reloads, which follow the server data sync that carries the list. Only EMI
 * instantiates this class, so nothing here loads when EMI is absent.
 */
@EmiEntrypoint
public final class MnxEmiPlugin implements EmiPlugin {
    private static final Viewer VIEWER = new Viewer();

    @Override
    public void register(EmiRegistry registry) {
        // MNX-088: the Recipes tab asks this adapter to draw recipes the way EMI does. Recipes change with each reload,
        // which EMI runs on its own thread.
        VIEWER.cache.clear();
        RecipeViewer.Slot.current = VIEWER;
        var alternatives = UnifiedItemsClient.alternatives();
        if (alternatives.isEmpty()) return;
        registry.removeEmiStacks(stack -> {
            ItemStack item = stack.getItemStack();
            return !item.isEmpty() && alternatives.contains(BuiltInRegistries.ITEM.getKey(item.getItem()));
        });
    }

    /** EMI as the drawer of recipes (MNX-088): the widgets EMI builds for a recipe, drawn where the tab wants them. */
    private static final class Viewer implements RecipeViewer {
        private final Map<ResourceLocation, Optional<Layout>> cache = new ConcurrentHashMap<>();

        @Override
        public String name() { return "EMI"; }

        @Override
        public Optional<Layout> layout(ResourceLocation recipe) {
            return cache.computeIfAbsent(recipe, Viewer::find);
        }

        private static Optional<Layout> find(ResourceLocation id) {
            try {
                EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(id);
                // A mod with only a JEI plugin: EMI holds its recipes under jei:/<namespace>/<path>.
                if (recipe == null) recipe = EmiApi.getRecipeManager().getRecipe(ResourceLocation.fromNamespaceAndPath("jei", "/" + id.getNamespace() + "/" + id.getPath()));
                if (recipe == null) return Optional.empty();
                int width = recipe.getDisplayWidth();
                int height = recipe.getDisplayHeight();
                List<Widget> widgets = new ArrayList<>();
                recipe.addWidgets(new WidgetHolder() {
                    @Override public int getWidth() { return width; }
                    @Override public int getHeight() { return height; }

                    @Override
                    public <T extends Widget> T add(T widget) {
                        widgets.add(widget);
                        return widget;
                    }
                });
                Component title = recipe.getCategory().getName();
                return Optional.of(new Layout() {
                    @Override public int width() { return width; }
                    @Override public int height() { return height; }
                    @Override public Component title() { return title; }

                    @Override
                    public void draw(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
                        g.pose().pushPose();
                        g.pose().translate(x, y, 0);
                        // ponytail: slots highlight under the pointer but show no tooltip; add EMI's tooltip rendering if asked.
                        for (Widget widget : widgets) widget.render(g, mouseX - x, mouseY - y, 0f);
                        g.pose().popPose();
                    }
                });
            } catch (RuntimeException e) {
                // EMI still loading, or a recipe it cannot lay out: listed without a drawing.
                return Optional.empty();
            }
        }

        @Override
        public void open(ResourceLocation item) {
            BuiltInRegistries.ITEM.getOptional(item).ifPresent(i -> EmiApi.displayRecipes(EmiStack.of(i)));
        }
    }
}
