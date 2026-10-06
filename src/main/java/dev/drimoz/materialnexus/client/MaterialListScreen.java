package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailRequest;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.MaterialListRequest;
import dev.drimoz.materialnexus.network.NexusQueries;
import dev.drimoz.materialnexus.network.PreviewRequest;
import dev.drimoz.materialnexus.network.RevertRequest;
import dev.drimoz.materialnexus.network.SuggestionsRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** Material browser: server-side filter and pagination, one request per change. */
public final class MaterialListScreen extends Screen {
    private final boolean readOnly;
    private final boolean canRevert;
    private String query = "";
    private MaterialListPayload page;
    private MaterialList list;
    private Button previous;
    private Button next;

    public MaterialListScreen(boolean readOnly, boolean canRevert) {
        super(Component.translatable("screen.materialnexus.title"));
        this.readOnly = readOnly;
        this.canRevert = canRevert;
    }

    boolean readOnly() { return readOnly; }

    /** Rebuilds the widgets, e.g. so the Preview button shows a new pending count. */
    void refresh() { rebuildWidgets(); }

    @Override
    protected void init() {
        EditBox search = new EditBox(font, width / 2 - 100, 22, 200, 18, Component.translatable("screen.materialnexus.search"));
        search.setMaxLength(NexusQueries.MAX_QUERY);
        search.setHint(Component.translatable("screen.materialnexus.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            request(0);
        });
        addRenderableWidget(search);

        list = addRenderableWidget(new MaterialList(minecraft, width, height - 78, 46));
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> request(page.page() - 1))
                .bounds(width / 2 - 100, height - 26, 20, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> request(page.page() + 1))
                .bounds(width / 2 + 80, height - 26, 20, 20).build());
        if (!readOnly) {
            addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.preview_button", PendingChanges.size()),
                            b -> PacketDistributor.sendToServer(new PreviewRequest(PendingChanges.all(), false)))
                    .bounds(width - 108, height - 26, 100, 20).build());
            // Always available while editing: with nothing pending it previews regenerating the pack from the policy files.
            Button revert = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.revert"), b -> {
                PacketDistributor.sendToServer(RevertRequest.INSTANCE);
                // The server reopens Material Nexus once the reload has finished.
                minecraft.setScreen(null);
            }).bounds(width - 108, 22, 100, 18).build());
            revert.active = canRevert;
            // Accepts every current suggestion as a pending choice; still reviewed in Preview before anything is written.
            addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.unify_all"),
                            b -> PacketDistributor.sendToServer(SuggestionsRequest.INSTANCE))
                    .bounds(8, 22, 120, 18).build());
        }

        if (page == null) {
            previous.active = false;
            next.active = false;
            request(0);
        } else {
            show(page);
        }
    }

    /** Responses can arrive out of order while typing; only the one matching the current filter is shown. */
    void accept(MaterialListPayload payload) {
        if (payload.query().equals(query.strip().toLowerCase(Locale.ROOT))) show(payload);
    }

    private void show(MaterialListPayload payload) {
        page = payload;
        list.show(payload.entries());
        previous.active = payload.page() > 0;
        next.active = payload.page() < payload.pageCount() - 1;
    }

    private void request(int pageIndex) {
        PacketDistributor.sendToServer(new MaterialListRequest(pageIndex, query));
    }

    private void openDetail(String material) {
        PacketDistributor.sendToServer(new MaterialDetailRequest(material));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (page != null) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.page", page.page() + 1, page.pageCount()), width / 2, height - 20, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("screen.materialnexus.materials", page.totalMatches()), 8, height - 20, 0xAAAAAA);
            if (page.entries().isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.empty"), width / 2, 60, 0xAAAAAA);
            }
        }
        if (readOnly) {
            graphics.drawString(font, Component.translatable("screen.materialnexus.read_only"), 8, height - 10, 0xFFAA00);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private final class MaterialList extends ObjectSelectionList<Row> {
        MaterialList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
        }

        void show(List<MaterialListPayload.Summary> entries) {
            replaceEntries(entries.stream().map(Row::new).toList());
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() { return Math.min(width - 40, 360); }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final MaterialListPayload.Summary summary;
        private final Component name;

        Row(MaterialListPayload.Summary summary) {
            this.summary = summary;
            this.name = Names.material(summary.material());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(font, name, left + 4, top + 2, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("screen.materialnexus.row",
                    summary.forms(), summary.providers(), summary.duplicateForms()), left + 4, top + 12, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            openDetail(summary.material());
            return true;
        }

        @Override
        public Component getNarration() { return name; }
    }
}
