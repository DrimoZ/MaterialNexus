package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Shared drawing of the Material Nexus screens (MNX-049, MNX-094): pills, buttons, item slots and click areas, in {@link Theme} roles. */
final class Ui {
    /** Status of one material/form, from what decides it most. */
    enum Status {
        PENDING("pending"), UNIFIED("unified"), SUGGESTION("suggestion"), SINGLE("single"), RESET("reset"), NOTHING("nothing"), ABSENT("absent");

        final String key;

        Status(String key) {
            this.key = key;
        }

        /** To decide is the warning colour, a choice not applied yet the information one; what asks nothing stays quiet. */
        int color() {
            return switch (this) {
                case PENDING, RESET -> Theme.INFO;
                case UNIFIED -> Theme.OK;
                case SUGGESTION -> Theme.WARN;
                case SINGLE, NOTHING, ABSENT -> Theme.FAINT;
            };
        }

        Component label() { return Component.translatable("screen.materialnexus.status." + key); }
    }

    record Hit(int x, int y, int w, int h, Runnable left, Runnable right, List<Component> tooltip) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Click areas of a custom-drawn panel, rebuilt every frame. */
    static final class Hits {
        private final List<Hit> hits = new ArrayList<>();

        void clear() { hits.clear(); }

        void add(int x, int y, int w, int h, Runnable left, Runnable right, List<Component> tooltip) {
            hits.add(new Hit(x, y, w, h, left, right, tooltip));
        }

        boolean click(double mx, double my, int button) {
            for (Hit h : hits) {
                if (!h.contains(mx, my)) continue;
                Runnable action = button == 1 ? h.right() : h.left();
                if (action == null) return false;
                action.run();
                return true;
            }
            return false;
        }

        void tooltip(GuiGraphics g, Font font, int mx, int my) {
            for (Hit h : hits) {
                if (h.contains(mx, my) && !h.tooltip().isEmpty()) {
                    g.renderComponentTooltip(font, h.tooltip(), mx, my);
                    return;
                }
            }
        }
    }

    private Ui() { }

    /** A label in {@code color} on that colour mixed into the background, with a full-colour edge; returns its width. */
    static int pill(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, Theme.mix(Theme.BG, color, 0.22f));
        g.fill(x, y, x + 1, y + 11, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }

    /** {@code text} cut to {@code width} with an ellipsis, so a cut name never reads as a whole one. */
    static String fit(Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    /** A flat button drawn by a panel that handles its own clicks; {@code primary} is the view's main action. */
    static void button(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean primary, boolean over, boolean active) {
        int fill = !active ? Theme.RAISED : primary ? (over ? Theme.mix(Theme.ACCENT, Theme.TEXT, 0.18f) : Theme.ACCENT) : over ? Theme.SEL : Theme.RAISED;
        g.fill(x, y, x + w, y + h, fill);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, !active ? Theme.FAINT : primary ? Theme.ON : Theme.TEXT, false);
    }

    /** A bin, 9 by 10: lid, handle, and a body with two stripes (MNX-117). */
    static void bin(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y, x + 6, y + 1, color);
        g.fill(x, y + 1, x + 9, y + 2, color);
        g.fill(x + 1, y + 3, x + 2, y + 10, color);
        g.fill(x + 7, y + 3, x + 8, y + 10, color);
        g.fill(x + 1, y + 9, x + 8, y + 10, color);
        g.fill(x + 3, y + 4, x + 4, y + 8, color);
        g.fill(x + 5, y + 4, x + 6, y + 8, color);
    }

    /** The danger variant (docs/22 4.5): red text on the secondary fill, for an action that switches something off. */
    static void dangerButton(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean over) {
        g.fill(x, y, x + w, y + h, over ? Theme.SEL : Theme.RAISED);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, Theme.BAD, false);
    }

    /**
     * The shape that goes with a status colour, 6 px: a filled dot for what is to decide, a ring for a choice not applied
     * yet, a tick for unified. What asks nothing has no mark. Colour is never the only sign.
     */
    static void mark(GuiGraphics g, Status status, int x, int y) {
        switch (status) {
            case SUGGESTION -> {
                g.fill(x + 1, y, x + 5, y + 6, Theme.WARN);
                g.fill(x, y + 1, x + 6, y + 5, Theme.WARN);
            }
            case PENDING, RESET -> g.renderOutline(x, y, 6, 6, Theme.INFO);
            case UNIFIED -> {
                int[][] tick = {{0, 3}, {1, 4}, {2, 3}, {3, 2}, {4, 1}, {5, 0}};
                for (int[] p : tick) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, Theme.OK);
            }
            default -> { }
        }
    }

    /** A switch, 20 by 10: the knob on the right and the accent colour when on. */
    static void toggle(GuiGraphics g, int x, int y, boolean on) {
        g.fill(x, y, x + 20, y + 10, on ? Theme.ACCENT : Theme.CTRL);
        g.fill(on ? x + 12 : x + 2, y + 2, on ? x + 18 : x + 8, y + 8, Theme.BG);
    }

    /** The fold mark of a group: pointing right when folded, down when open. */
    static void caret(GuiGraphics g, int x, int y, boolean open, int color) {
        for (int i = 0; i < 3; i++) {
            if (open) g.fill(x + i, y + 1 + i, x + 5 - i, y + 2 + i, color);
            else g.fill(x + 1 + i, y + i, x + 2 + i, y + 5 - i, color);
        }
    }

    /** An 18px item slot on the mid-grey well, with a 1px outline in {@code outline} (0 for none); {@code dim} fades it. */
    static void slot(GuiGraphics g, ResourceLocation item, int x, int y, int outline, boolean dim) {
        if (outline != 0) g.fill(x - 1, y - 1, x + 19, y + 19, outline);
        g.fill(x, y, x + 18, y + 18, Theme.WELL);
        g.renderItem(Names.stack(item), x + 1, y + 1);
        if (dim) g.fill(x, y, x + 18, y + 18, Theme.BG & 0x00FFFFFF | 0x99000000);
    }
}
