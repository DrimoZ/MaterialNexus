package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Optional;

/**
 * A recipe drawn by the installed viewer in a box next to the pointer (MNX-088): hovering a recipe row or a route of
 * the "How it's made" tab shows what it looks like. Without a viewer nothing is drawn and the caller keeps its tooltip.
 */
final class RecipeHover {
    private RecipeHover() { }

    static Optional<RecipeViewer.Layout> layout(ResourceLocation recipe) {
        RecipeViewer viewer = RecipeViewer.Slot.current;
        return viewer == null || recipe == null ? Optional.empty() : viewer.layout(recipe);
    }

    /** False when no viewer can draw this recipe. */
    static boolean draw(GuiGraphics g, Font font, ResourceLocation recipe, Component caption, int mx, int my) {
        Optional<RecipeViewer.Layout> found = layout(recipe);
        if (found.isEmpty()) return false;
        RecipeViewer.Layout l = found.get();
        int inner = Math.max(l.width(), 160);
        List<FormattedCharSequence> lines = font.split(caption, inner);
        int w = inner + 12;
        int h = 18 + l.height() + 6 + lines.size() * 10 + 4;
        Screen screen = Minecraft.getInstance().screen;
        int sw = screen == null ? g.guiWidth() : screen.width;
        int sh = screen == null ? g.guiHeight() : screen.height;
        // Beside the pointer, on the side that has room.
        int x = Math.max(4, mx + 12 + w > sw ? mx - 12 - w : mx + 12);
        int y = Math.clamp(my + 12, 4, Math.max(4, sh - h - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
        g.fill(x, y, x + w, y + h, Theme.RAISED);
        g.drawString(font, Component.literal(font.plainSubstrByWidth(l.title().getString(), inner)).withStyle(ChatFormatting.BOLD), x + 6, y + 6, Theme.TEXT, false);
        // The pointer is never over the box: no slot of the layout is highlighted.
        l.draw(g, x + 6 + (inner - l.width()) / 2, y + 18, -1, -1);
        int cy = y + 18 + l.height() + 6;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 6, cy, Theme.MUTED, false);
            cy += 10;
        }
        g.pose().popPose();
        return true;
    }
}
