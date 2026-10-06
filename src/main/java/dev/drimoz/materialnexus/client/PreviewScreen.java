package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.network.PreviewPayload;
import dev.drimoz.materialnexus.network.PreviewRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Every change, validated by the server, before anything is written (ADR-009). */
public final class PreviewScreen extends Screen {
    private final Screen parent;
    private final PreviewPayload preview;

    PreviewScreen(Screen parent, PreviewPayload preview) {
        super(Component.translatable("screen.materialnexus.preview_title"));
        this.parent = parent;
        this.preview = preview;
    }

    @Override
    protected void init() {
        Lines lines = addRenderableWidget(new Lines(minecraft, width, height - 64, 28));
        preview.entries().forEach(e -> lines.add(new Line(e)));

        Button apply = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.apply"), b -> apply())
                .bounds(width / 2 - 154, height - 28, 100, 20).build());
        apply.active = preview.entries().stream().anyMatch(PolicyEditor.Entry::valid);
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.discard"), b -> {
            PendingChanges.clear();
            onClose();
        }).bounds(width / 2 - 50, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 + 54, height - 28, 100, 20).build());
    }

    private void apply() {
        PacketDistributor.sendToServer(new PreviewRequest(PendingChanges.all(), true));
        PendingChanges.clear();
        // The server reopens Material Nexus once the reload has finished.
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (preview.entries().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.no_changes"), width / 2, 40, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class Lines extends ObjectSelectionList<Line> {
        Lines(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 12);
        }

        void add(Line line) { addEntry(line); }

        @Override
        public int getRowWidth() { return Math.min(width - 40, 460); }
    }

    private final class Line extends ObjectSelectionList.Entry<Line> {
        private final Component text;
        private final int color;

        Line(PolicyEditor.Entry e) {
            Component material = Names.material(e.material());
            Component form = Names.form(e.form());
            if (e.valid()) {
                Component from = e.from().map(id -> (Component) Component.literal(id.toString()))
                        .orElse(Component.translatable("screen.materialnexus.none"));
                text = Component.translatable("screen.materialnexus.preview_line", material, form, from, e.to().toString());
                color = 0x55FF55;
            } else {
                text = Component.translatable("screen.materialnexus.preview_invalid", material, form, e.to().toString());
                color = 0xFF5555;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width - 4), left + 2, top + 1, color);
        }

        @Override
        public Component getNarration() { return text; }
    }
}
