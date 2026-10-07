package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Left column of {@link NexusScreen}: materials of the current page, with a status dot each. */
final class MaterialSidebar extends ObjectSelectionList<MaterialSidebar.Entry> {
    static final int DONE = 0xFF55FF55;
    static final int PENDING = 0xFFFFCC33;
    static final int NOTHING = 0xFF777777;

    private final Consumer<String> onSelect;
    private String selected;

    MaterialSidebar(Minecraft minecraft, int width, int height, int y, Consumer<String> onSelect) {
        super(minecraft, width, height, y, 22);
        this.onSelect = onSelect;
    }

    void show(List<MaterialListPayload.Summary> entries, String selectedMaterial) {
        selected = selectedMaterial;
        replaceEntries(entries.stream().map(Entry::new).toList());
        children().stream().filter(e -> e.summary.material().equals(selected)).findFirst().ifPresent(this::setSelected);
    }

    @Override
    public int getRowWidth() { return width - 12; }

    @Override
    protected int getScrollbarPosition() { return getX() + width - 6; }

    /** Green: every duplicated form is decided. Amber: duplicates still only suggested. Grey: nothing to unify. */
    static int status(MaterialListPayload.Summary s) {
        if (s.duplicateForms() == 0) return NOTHING;
        return s.unifiedForms() >= s.duplicateForms() ? DONE : PENDING;
    }

    final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final MaterialListPayload.Summary summary;
        private final Component name;

        Entry(MaterialListPayload.Summary summary) {
            this.summary = summary;
            this.name = Names.material(summary.material());
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            var font = Minecraft.getInstance().font;
            g.fill(left + 2, top + 6, left + 7, top + 11, status(summary));
            g.drawString(font, font.plainSubstrByWidth(name.getString(), width - 16), left + 12, top + 2, 0xFFFFFF);
            g.drawString(font, Component.translatable("screen.materialnexus.sidebar_counts", summary.forms(), summary.duplicateForms()), left + 12, top + 12, 0x888888);
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
