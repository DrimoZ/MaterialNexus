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

    /** Room around the recipe: inside the panel the screen gives it, or for the border a viewer paints around its own. */
    private static int pad(RecipeViewer.Layout l) { return l.framed() ? 4 : 7; }

    static int width(RecipeViewer.Layout l) { return l.width() + 2 * pad(l); }

    /**
     * The recipe on a panel that hugs it, at {@code x, y}. A viewer that paints no panel gets one in the grey of a
     * vanilla slot: its slots, arrows and texts keep their own colours, so the ground is made for them rather than
     * taken from the theme. Returns the height used.
     */
    static int stage(GuiGraphics g, RecipeViewer.Layout l, int x, int y, int mx, int my) {
        int p = pad(l);
        if (!l.framed()) {
            g.fill(x, y, x + width(l), y + l.height() + 2 * p, Theme.BORDER);
            g.fill(x + 1, y + 1, x + width(l) - 1, y + l.height() + 2 * p - 1, Theme.TRAY);
        }
        l.draw(g, x + p, y + p, mx, my);
        return l.height() + 2 * p;
    }

    /**
     * False when no viewer can draw this recipe. {@code caption} may be null; an id (no space in it) is cut with an
     * ellipsis, never broken in the middle.
     */
    static boolean draw(GuiGraphics g, Font font, ResourceLocation recipe, Component caption, int mx, int my) {
        Optional<RecipeViewer.Layout> found = layout(recipe);
        if (found.isEmpty()) return false;
        RecipeViewer.Layout l = found.get();
        Component title = Component.literal(l.title().getString()).withStyle(ChatFormatting.BOLD);
        int inner = Math.max(Math.max(width(l), font.width(title)), caption == null ? 0 : 176);
        if (caption != null && !caption.getString().contains(" ") && font.width(caption) > inner) {
            caption = Component.literal(font.plainSubstrByWidth(caption.getString(), inner - font.width("…")) + "…");
        }
        List<FormattedCharSequence> lines = caption == null ? List.of() : font.split(caption, inner);
        int w = inner + 16;
        int h = 8 + 10 + 6 + l.height() + 2 * pad(l) + (lines.isEmpty() ? 0 : 6 + lines.size() * 10) + 8;
        Screen screen = Minecraft.getInstance().screen;
        int sw = screen == null ? g.guiWidth() : screen.width;
        int sh = screen == null ? g.guiHeight() : screen.height;
        // Beside the pointer, on the side that has room.
        int x = Math.max(4, mx + 12 + w > sw ? mx - 12 - w : mx + 12);
        int y = Math.clamp(my + 14, 4, Math.max(4, sh - h - 4));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
        g.fill(x, y, x + w, y + h, Theme.RAISED);
        g.drawString(font, title, x + 8, y + 8, Theme.TEXT, false);
        // The pointer is never over the box: no slot of the layout is highlighted.
        int cy = y + 24 + stage(g, l, x + 8, y + 24, -1, -1) + 6;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 8, cy, Theme.MUTED, false);
            cy += 10;
        }
        g.pose().popPose();
        return true;
    }
}
