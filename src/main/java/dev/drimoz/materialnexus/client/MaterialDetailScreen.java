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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One material: per form, the canonical item, why it was chosen, its interchangeable alternatives
 * and the providers deliberately left alone. Clicking a form cycles every provider into a pending
 * change, the player having the final say; nothing is written before Preview / Apply.
 */
public final class MaterialDetailScreen extends Screen {
    private static final int ROW_HEIGHT = 59;

    private final MaterialListScreen parent;
    private final MaterialDetailPayload detail;

    MaterialDetailScreen(MaterialListScreen parent, MaterialDetailPayload detail) {
        super(Names.material(detail.material()));
        this.parent = parent;
        this.detail = detail;
    }

    @Override
    protected void init() {
        FormList list = addRenderableWidget(new FormList(minecraft, width, height - 76, 28));
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
        if (!parent.readOnly()) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.click_to_change"), width / 2, height - 42, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class FormList extends ObjectSelectionList<Row> {
        FormList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        void add(Row row) { addEntry(row); }

        @Override
        public int getRowWidth() { return Math.min(width - 40, 440); }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final String form;
        private final ResolvedForm resolved;
        private final List<ResourceLocation> providers = new ArrayList<>();
        private final Component formName;
        private final Component why;
        private final Component alternatives;
        private final Component notUnified;
        private final Component ignored;

        Row(MaterialDetailPayload.FormView view) {
            form = view.form();
            resolved = view.resolved();
            resolved.canonical().ifPresent(providers::add);
            providers.addAll(resolved.alternatives());
            resolved.notUnified().forEach(n -> providers.add(n.item()));
            formName = Names.form(form);
            why = Component.translatable("screen.materialnexus.why",
                    Component.translatable(resolved.reasonKey(), resolved.reasonArgs().toArray()),
                    Component.translatable("materialnexus.source." + Names.lowerName(resolved.source())),
                    Component.translatable("materialnexus.confidence." + Names.lowerName(resolved.confidence())));
            alternatives = resolved.alternatives().isEmpty()
                    ? Component.translatable("screen.materialnexus.no_alternatives")
                    : Component.translatable("screen.materialnexus.alternatives",
                            resolved.alternatives().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
            notUnified = resolved.notUnified().isEmpty() ? null : notUnifiedLine(resolved.notUnified());
            ignored = resolved.ignoredOverride()
                    .map(id -> (Component) Component.translatable("materialnexus.why.override_ignored", id.toString()))
                    .orElse(null);
        }

        private static Component notUnifiedLine(List<ResolvedForm.NotUnified> items) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < items.size(); i++) {
                ResolvedForm.NotUnified n = items.get(i);
                if (i > 0) list.append(", ");
                list.append(Component.translatable("screen.materialnexus.not_unified_item", n.item().toString(),
                        Component.translatable(n.reasonKey(), n.reasonArgs().toArray())));
            }
            return Component.translatable("screen.materialnexus.not_unified", list);
        }

        private Optional<ResourceLocation> shown() {
            return PendingChanges.get(detail.material(), form).or(resolved::canonical);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Optional<ResourceLocation> shown = shown();
            ItemStack icon = shown.map(Names::stack).orElse(ItemStack.EMPTY);
            Component item = shown.isEmpty() ? Component.translatable("screen.materialnexus.no_canonical")
                    : icon.isEmpty() ? Component.literal(shown.get().toString()) : icon.getHoverName();
            graphics.renderItem(icon, left + 2, top + 2);
            int x = left + 24;
            int textWidth = width - 28;
            line(graphics, Component.translatable("screen.materialnexus.form_line", formName, item), x, top + 2, textWidth, 0xFFFFFF);
            line(graphics, why, x, top + 13, textWidth, 0xAAAAAA);
            line(graphics, alternatives, x, top + 24, textWidth, 0x888888);
            if (notUnified != null) line(graphics, notUnified, x, top + 35, textWidth, 0x6688AA);
            if (!shown.equals(resolved.canonical())) {
                line(graphics, Component.translatable("screen.materialnexus.pending", shown.map(ResourceLocation::toString).orElse("")), x, top + 46, textWidth, 0x55FF55);
            } else if (ignored != null) {
                line(graphics, ignored, x, top + 46, textWidth, 0xFFAA00);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (parent.readOnly() || providers.isEmpty()) return false;
            int current = shown().map(providers::indexOf).orElse(-1);
            ResourceLocation next = providers.get((current + 1) % providers.size());
            PendingChanges.set(detail.material(), form, next, resolved.canonical());
            return true;
        }

        /** Long lines are cut to the row width rather than overflowing into the next column. */
        private void line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), maxWidth), x, y, color);
        }

        @Override
        public Component getNarration() { return formName; }
    }
}
