package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Material (or form) list of the main screen (MNX-049): a status edge (amber: duplicates to decide, green: all
 * decided, grey: nothing to unify), the item that stands for it, its name, and how many forms are still to decide.
 */
final class MaterialSidebar extends ObjectSelectionList<MaterialSidebar.Entry> {
    private final Consumer<String> onSelect;
    private String selected;

    MaterialSidebar(Minecraft minecraft, int width, int height, int y, Consumer<String> onSelect) {
        super(minecraft, width, height, y, y + height, 20);
        this.onSelect = onSelect;
        // The main screen draws its own flat background (MNX-049).
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }

    void show(List<MaterialListPayload.Summary> entries, String selectedMaterial, boolean byForm) {
        selected = selectedMaterial;
        replaceEntries(entries.stream().map(s -> new Entry(s, byForm)).toList());
        children().stream().filter(e -> e.summary.material().equals(selected)).findFirst().ifPresent(this::setSelected);
    }

    @Override
    public int getRowWidth() { return width - 10; }

    @Override
    protected int getScrollbarPosition() { return x0 + width - 5; }

    static int status(MaterialListPayload.Summary s) {
        if (s.duplicateForms() == 0) return Ui.NEUTRAL;
        return s.toDecide() > 0 ? Ui.WARNING : Ui.SUCCESS;
    }

    final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final MaterialListPayload.Summary summary;
        private final Component name;

        Entry(MaterialListPayload.Summary summary, boolean byForm) {
            this.summary = summary;
            this.name = byForm ? Names.form(summary.material()) : Names.material(summary.material());
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            var font = Minecraft.getInstance().font;
            boolean isSelected = summary.material().equals(selected);
            if (isSelected) g.fill(left - 2, top - 1, left + width, top + height, 0x30FFFFFF);
            else if (hovering) g.fill(left - 2, top - 1, left + width, top + height, 0x14FFFFFF);
            g.fill(left - 2, top - 1, left, top + height, status(summary));
            summary.icon().ifPresent(icon -> g.renderItem(Names.stack(icon), left + 2, top));
            String count = summary.toDecide() > 0 ? String.valueOf(summary.toDecide()) : "";
            int countWidth = font.width(count);
            g.drawString(font, font.plainSubstrByWidth(name.getString(), width - 26 - countWidth - 6), left + 21, top + 5, Ui.TEXT, false);
            if (!count.isEmpty()) g.drawString(font, count, left + width - countWidth - 4, top + 5, Ui.WARNING, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selected = summary.material();
            onSelect.accept(summary.material());
            return true;
        }

        @Override
        public Component getNarration() { return name; }
    }
}
