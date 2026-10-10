package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Material list of the main screen (MNX-049, MNX-095): the item that stands for it, its name, and how many forms are
 * still to decide. A material with no duplicate is muted. Drawn by the screen, with its own scrollbar.
 */
final class MaterialSidebar {

    private final Consumer<String> onSelect;
    private final Ui.Hits hits = new Ui.Hits();
    private List<MaterialListPayload.Summary> entries = List.of();
    private String selected;
    private int x, y, width, height;
    private double scroll;

    MaterialSidebar(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        // The main screen draws its own flat background (MNX-049).
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    void show(List<MaterialListPayload.Summary> entries, String selected) {
        if (!entries.equals(this.entries)) scroll = 0;
        this.entries = entries;
        this.selected = selected;
    }

    void select(String material) { selected = material; }

    private boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }

    boolean click(double mx, double my, int button) {
        return contains(mx, my) && hits.click(mx, my, button);
    }

    boolean scroll(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        scroll = Math.clamp(scroll - delta * Theme.ROW_ITEM, 0, Math.max(0, entries.size() * Theme.ROW_ITEM - height));
        return true;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        int content = entries.size() * Theme.ROW_ITEM;
        scroll = Math.clamp(scroll, 0, Math.max(0, content - height));
        boolean inside = contains(mx, my);
        int rowWidth = width - (content > height ? 6 : 0);
        g.enableScissor(x, y, x + width, y + height);
        int top = y - (int) scroll;
        for (MaterialListPayload.Summary s : entries) {
            if (top + Theme.ROW_ITEM > y && top < y + height) {
                boolean isSelected = s.material().equals(selected);
                boolean hover = inside && my >= top && my < top + Theme.ROW_ITEM && mx < x + rowWidth;
                if (isSelected) g.fill(x, top, x + rowWidth, top + Theme.ROW_ITEM, Theme.SEL);
                else if (hover) g.fill(x, top, x + rowWidth, top + Theme.ROW_ITEM, Theme.RAISED);
                // The place of the icon is kept even without one, so names stay aligned.
                if (s.icon().isPresent()) Ui.slot(g, s.icon().get(), x + 6, top + 2 + Theme.ROW_PAD, 0, false);
                String count = s.toDecide() > 0 ? String.valueOf(s.toDecide()) : "";
                int countWidth = font.width(count);
                Component name = Names.material(s.material());
                g.drawString(font, font.plainSubstrByWidth(name.getString(), rowWidth - 36 - countWidth - 8), x + 30, top + 7 + Theme.ROW_PAD,
                        isSelected || s.duplicateForms() > 0 ? Theme.TEXT : Theme.MUTED, false);
                if (!count.isEmpty()) g.drawString(font, count, x + rowWidth - countWidth - 8, top + 7 + Theme.ROW_PAD, Theme.WARN, false);
                if (inside) hits.add(x, top, rowWidth, Theme.ROW_ITEM, () -> { selected = s.material(); onSelect.accept(s.material()); }, null, List.of());
            }
            top += Theme.ROW_ITEM;
        }
        g.disableScissor();
        if (content > height) {
            g.fill(x + width - 4, y, x + width, y + height, Theme.RAISED);
            int bar = Math.max(12, height * height / content);
            int at = (int) (scroll * (height - bar) / (content - height));
            g.fill(x + width - 4, y + at, x + width, y + at + bar, Theme.CTRL);
        }
    }
}
