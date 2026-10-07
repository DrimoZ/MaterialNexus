package dev.drimoz.materialnexus.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Shared look of the Material Nexus screens (MNX-049): one palette, pills, item slots and click areas. */
final class Ui {
    static final int BG = 0xFA101014;
    static final int PANEL = 0xC0181820;
    static final int ROW = 0x40FFFFFF;
    static final int LINE = 0x30FFFFFF;
    static final int TEXT = 0xFFFFFF;
    static final int MUTED = 0x9A9AA6;
    static final int FAINT = 0x66666F;
    static final int ACCENT = 0xFF5A9BE6;
    static final int SUCCESS = 0xFF4CC46A;
    static final int WARNING = 0xFFE8B23A;
    static final int DANGER = 0xFFD9534F;
    static final int NEUTRAL = 0xFF5A5A66;

    /** Status of one material/form, from what decides it most. */
    enum Status {
        PENDING("pending", ACCENT), UNIFIED("unified", SUCCESS), SUGGESTION("suggestion", WARNING),
        SINGLE("single", NEUTRAL), RESET("reset", WARNING), NOTHING("nothing", NEUTRAL), ABSENT("absent", 0xFF3A3A44);

        final String key;
        final int color;

        Status(String key, int color) {
            this.key = key;
            this.color = color;
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

    /** A label on a tinted background with a colored edge; returns its width. */
    static int pill(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, (color & 0x00FFFFFF) | 0x50000000);
        g.fill(x, y, x + 1, y + 11, color);
        g.drawString(font, text, x + 4, y + 2, color | 0xFF000000, false);
        return w;
    }

    /** An 18px item slot with a 1px outline in {@code outline} (0 for none); {@code dim} greys it out. */
    static void slot(GuiGraphics g, ResourceLocation item, int x, int y, int outline, boolean dim) {
        if (outline != 0) g.fill(x - 1, y - 1, x + 19, y + 19, outline);
        g.fill(x, y, x + 18, y + 18, 0xFF22222A);
        g.renderItem(Names.stack(item), x + 1, y + 1);
        if (dim) g.fill(x, y, x + 18, y + 18, 0x99101014);
    }
}
