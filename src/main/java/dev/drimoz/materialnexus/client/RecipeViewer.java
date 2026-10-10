package dev.drimoz.materialnexus.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * What the Recipes tab asks of a recipe viewer (MNX-088): draw a loaded recipe the way the viewer does, name its
 * machine, and open the viewer on an item. Material Nexus draws no recipe itself. The adapter of an installed viewer
 * (client/compat) puts itself in {@link Slot#current}; with none, the tab lists recipes without drawing them.
 */
public interface RecipeViewer {
    /** The installed viewer, or null. Set and cleared by its adapter, on the client thread. */
    final class Slot {
        public static RecipeViewer current;

        private Slot() { }
    }

    /** One recipe as the viewer lays it out. */
    interface Layout {
        int width();

        int height();

        /** The viewer's name for the machine or kind of recipe ("Crushing", "Smelting"). */
        Component title();

        void draw(GuiGraphics g, int x, int y, int mouseX, int mouseY);
    }

    /** "JEI", "EMI". */
    String name();

    /** The layout of a recipe the game has loaded; empty when it is not loaded or the viewer cannot draw it here. */
    Optional<Layout> layout(ResourceLocation recipe);

    /** Opens the viewer on the recipes that make {@code item}. */
    void open(ResourceLocation item);
}
