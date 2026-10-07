package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
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

/** Every change, validated by the server, before anything is written (ADR-009): choices, then pack effects. */
public final class PreviewScreen extends Screen {
    private final Screen parent;
    private final PreviewPayload preview;

    PreviewScreen(Screen parent, PreviewPayload preview) {
        super(Component.translatable("screen.materialnexus.preview_title"));
        this.parent = parent;
        this.preview = preview;
    }

    private boolean hasChanges() {
        return preview.entries().stream().anyMatch(PolicyEditor.Entry::valid) || !preview.added().isEmpty() || !preview.removed().isEmpty()
                || !PendingChanges.processes().isEmpty() || !PendingChanges.creations().isEmpty();
    }

    @Override
    protected void init() {
        Lines lines = addRenderableWidget(new Lines(minecraft, width, height - 64, 28));
        preview.entries().forEach(e -> lines.add(choiceLine(e)));
        PendingChanges.processes().keySet().stream().sorted().forEach(form -> lines.add(
                new Line(Component.translatable("screen.materialnexus.preview_process", Names.form(form)), 0x55FF55)));
        preview.added().forEach(e -> lines.add(effectLine(e, "screen.materialnexus.preview_added", 0x55FFFF)));
        preview.removed().forEach(e -> lines.add(effectLine(e, "screen.materialnexus.preview_removed", 0xFFFF55)));

        Button apply = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.apply"), b -> apply())
                .bounds(width / 2 - 154, height - 28, 100, 20).build());
        apply.active = hasChanges();
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.discard"), b -> {
            PendingChanges.clear();
            onClose();
        }).bounds(width / 2 - 50, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 + 54, height - 28, 100, 20).build());
    }

    private void apply() {
        PacketDistributor.sendToServer(new PreviewRequest(PendingChanges.all(), true, preview.preset(), PendingChanges.processes(), PendingChanges.creations()));
        // The server reopens Material Nexus once the reload has finished; pending choices are kept until then.
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
        preview.preset().ifPresent(id -> graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.preview_preset",
                Component.translatableWithFallback("materialnexus.preset." + id.getPath(), id.getPath())), width / 2, 19, 0x88AAFF));
        if (!hasChanges()) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.no_changes"), width / 2, 40, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private Line choiceLine(PolicyEditor.Entry e) {
        Component material = Names.material(e.material());
        Component form = Names.form(e.form());
        if (!e.valid()) {
            return new Line(Component.translatable("screen.materialnexus.preview_invalid", material, form, e.to().toString()), 0xFF5555);
        }
        Component from = e.from().map(id -> (Component) Component.literal(id.toString()))
                .orElse(Component.translatable("screen.materialnexus.none"));
        return new Line(Component.translatable("screen.materialnexus.preview_line", material, form, from, e.to().toString()), 0x55FF55);
    }

    private Line effectLine(PackContent.Effect e, String prefixKey, int color) {
        Component effect = switch (e.kind()) {
            case PackContent.TAG_REMOVE -> Component.translatable("screen.materialnexus.effect.tag_remove", e.target().toString(), e.item().toString());
            case PackContent.ITEM_CONVERSION -> Component.translatable("screen.materialnexus.effect.item_conversion", e.item().toString(), e.target().toString());
            case RecipeRewrites.REWRITE -> Component.translatable("screen.materialnexus.effect.recipe_rewrite", e.target().toString(), e.item().toString());
            case RecipeRewrites.DISABLE -> Component.translatable("screen.materialnexus.effect.recipe_disable", e.target().toString());
            case RecipeRewrites.UNSUPPORTED -> Component.translatable("screen.materialnexus.effect.recipe_unsupported", e.target().toString(), e.item().toString());
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.PROCESS -> processLine(e);
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.DISABLE -> Component.translatable("screen.materialnexus.effect.process_disable", e.target().toString(), e.item().toString());
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.UNSUPPORTED -> Component.translatable("screen.materialnexus.effect.process_unsupported", e.target().toString(), e.item().toString());
            case PackContent.RECIPE_INVALID -> Component.translatable("screen.materialnexus.effect.recipe_invalid", e.target().toString(), e.item().toString());
            case PackContent.ITEM_CREATE -> Component.translatable("screen.materialnexus.effect.item_create", e.target().toString(), e.item().toString());
            case PackContent.ALMOST_UNIFIED -> Component.translatable("screen.materialnexus.effect.almost_unified",
                    Component.translatable("materialnexus.au_domain." + e.target().getPath()));
            default -> Component.translatable("screen.materialnexus.effect.conversion", e.item().toString(), e.target().toString());
        };
        return new Line(Component.translatable(prefixKey, effect), color);
    }

    /** The ratio is in the generated recipe id: process/&lt;form&gt;/&lt;material&gt;/&lt;input&gt;/&lt;in&gt;/&lt;out&gt;/&lt;machine ns&gt;/&lt;machine path&gt;. */
    private static Component processLine(PackContent.Effect e) {
        String[] p = e.target().getPath().split("/", 8);
        if (p.length < 8) return Component.literal(e.target().toString());
        return Component.translatable("screen.materialnexus.effect.process_recipe", p[6] + ":" + p[7], p[4], Names.form(p[3]),
                p[5], e.item().toString(), Names.material(p[2]));
    }

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

        Line(Component text, int color) {
            this.text = text;
            this.color = color;
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
