package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.stream.Collectors;

/** One material: per form, the canonical item, why it was chosen and what else exists. Read-only until policy editing lands. */
public final class MaterialDetailScreen extends Screen {
    private static final int ROW_HEIGHT = 48;

    private final Screen parent;
    private final MaterialDetailPayload detail;

    MaterialDetailScreen(Screen parent, MaterialDetailPayload detail) {
        super(Names.material(detail.material()));
        this.parent = parent;
        this.detail = detail;
    }

    @Override
    protected void init() {
        FormList list = addRenderableWidget(new FormList(minecraft, width, height - 64, 28));
        detail.forms().forEach(view -> list.add(new Row(view)));
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class FormList extends ObjectSelectionList<Row> {
        FormList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        void add(Row row) { addEntry(row); }

        @Override
        public int getRowWidth() { return Math.min(width - 40, 420); }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final ItemStack icon;
        private final Component header;
        private final Component why;
        private final Component alternatives;
        private final Component ignored;

        Row(MaterialDetailPayload.FormView view) {
            ResolvedForm f = view.resolved();
            icon = Names.stack(f.canonical());
            Component item = icon.isEmpty() ? Component.literal(f.canonical().toString()) : icon.getHoverName();
            header = Component.translatable("screen.materialnexus.form_line", Names.form(view.form()), item);
            why = Component.translatable("screen.materialnexus.why",
                    Component.translatable(f.reasonKey(), f.reasonArgs().toArray()),
                    Component.translatable("materialnexus.source." + Names.lowerName(f.source())),
                    Component.translatable("materialnexus.confidence." + Names.lowerName(f.confidence())));
            alternatives = f.alternatives().isEmpty()
                    ? Component.translatable("screen.materialnexus.no_alternatives")
                    : Component.translatable("screen.materialnexus.alternatives",
                            f.alternatives().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
            ignored = f.ignoredOverride()
                    .map(id -> (Component) Component.translatable("materialnexus.why.override_ignored", id.toString()))
                    .orElse(null);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.renderItem(icon, left + 2, top + 2);
            int x = left + 24;
            int textWidth = width - 28;
            line(graphics, header, x, top + 2, textWidth, 0xFFFFFF);
            line(graphics, why, x, top + 13, textWidth, 0xAAAAAA);
            line(graphics, alternatives, x, top + 24, textWidth, 0x888888);
            if (ignored != null) line(graphics, ignored, x, top + 35, textWidth, 0xFFAA00);
        }

        /** Long ids are cut to the row width rather than overflowing into the next column. */
        private void line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), maxWidth), x, y, color);
        }

        @Override
        public Component getNarration() { return header; }
    }
}
