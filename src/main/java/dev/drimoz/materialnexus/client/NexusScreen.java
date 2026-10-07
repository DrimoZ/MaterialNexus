package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialDetailRequest;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.MaterialListRequest;
import dev.drimoz.materialnexus.network.NexusQueries;
import dev.drimoz.materialnexus.network.PreviewRequest;
import dev.drimoz.materialnexus.network.RecipeFamilyPayload;
import dev.drimoz.materialnexus.network.RecipeFamilyRequest;
import dev.drimoz.materialnexus.network.RevertRequest;
import dev.drimoz.materialnexus.network.SuggestionsRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Material Nexus main screen: materials on the left, the selected material on the right with Forms / Recipes /
 * Missing tabs, global actions on top. Everything shown comes from targeted server requests; choices stay
 * pending on the client until Preview / Apply.
 */
public final class NexusScreen extends Screen {
    private enum Tab { FORMS, RECIPES, MISSING }

    private final boolean readOnly;
    private final boolean canRevert;
    private final FormsPanel forms;
    private final RecipesPanel recipes;
    private String query = "";
    private MaterialListPayload page;
    private MaterialDetailPayload detail;
    private String selected;
    private Tab tab = Tab.FORMS;
    private MaterialSidebar sidebar;
    private Button preview;
    private int panelX, panelY, panelW, panelH;

    public NexusScreen(boolean readOnly, boolean canRevert) {
        super(Component.translatable("screen.materialnexus.title"));
        this.readOnly = readOnly;
        this.canRevert = canRevert;
        this.forms = new FormsPanel(readOnly, form -> { tab = Tab.RECIPES; rebuildWidgets(); recipesSelect(form); });
        this.recipes = new RecipesPanel(form -> PacketDistributor.sendToServer(new RecipeFamilyRequest(selected, form)));
    }

    boolean readOnly() { return readOnly; }

    void refresh() { rebuildWidgets(); }

    private void recipesSelect(String form) { recipes.select(form); }

    @Override
    protected void init() {
        int sideW = Math.max(120, Math.min(200, width / 4));
        if (!readOnly) {
            addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.unify_all"),
                    b -> PacketDistributor.sendToServer(SuggestionsRequest.INSTANCE)).bounds(width - 336, 3, 116, 18).build());
            Button revert = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.revert"), b -> {
                PacketDistributor.sendToServer(RevertRequest.INSTANCE);
                minecraft.setScreen(null);
            }).bounds(width - 216, 3, 104, 18).build());
            revert.active = canRevert;
            preview = addRenderableWidget(Button.builder(Component.empty(),
                    b -> PacketDistributor.sendToServer(new PreviewRequest(PendingChanges.all(), false))).bounds(width - 108, 3, 102, 18).build());
        }

        EditBox search = new EditBox(font, 6, 28, sideW - 12, 16, Component.translatable("screen.materialnexus.search"));
        search.setMaxLength(NexusQueries.MAX_QUERY);
        search.setHint(Component.translatable("screen.materialnexus.search"));
        search.setValue(query);
        search.setResponder(v -> { query = v; requestPage(0); });
        addRenderableWidget(search);
        sidebar = addRenderableWidget(new MaterialSidebar(minecraft, sideW, height - 48 - 26, 48, this::select));
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page != null) requestPage(page.page() - 1); })
                .bounds(6, height - 22, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if (page != null) requestPage(page.page() + 1); })
                .bounds(sideW - 26, height - 22, 20, 18).build());

        panelX = sideW + 10;
        panelW = width - panelX - 8;
        int tabY = 46;
        int tx = panelX;
        for (Tab t : Tab.values()) {
            Button b = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.tab." + t.name().toLowerCase(Locale.ROOT)),
                    x -> { tab = t; rebuildWidgets(); }).bounds(tx, tabY, 76, 18).build());
            b.active = tab != t && detail != null;
            tx += 80;
        }
        if (!readOnly && detail != null) {
            addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.unify_material"),
                    b -> acceptMaterialSuggestions()).bounds(panelX + panelW - 110, tabY, 110, 18).build());
        }
        panelY = tabY + 24;
        panelH = height - panelY - 6;
        forms.layout(panelX, panelY, panelW, panelH);
        recipes.layout(panelX, panelY, panelW, panelH);

        if (page == null) requestPage(0);
        else sidebar.show(page.entries(), selected);
    }

    private void requestPage(int index) {
        PacketDistributor.sendToServer(new MaterialListRequest(index, query));
    }

    private void select(String material) {
        selected = material;
        PacketDistributor.sendToServer(new MaterialDetailRequest(material));
    }

    /** Responses can arrive out of order while typing; only the one matching the current filter is shown. */
    void acceptList(MaterialListPayload payload) {
        if (!payload.query().equals(query.strip().toLowerCase(Locale.ROOT))) return;
        page = payload;
        sidebar.show(payload.entries(), selected);
        if (selected == null && !payload.entries().isEmpty()) select(payload.entries().getFirst().material());
    }

    void acceptDetail(MaterialDetailPayload payload) {
        if (!payload.material().equals(selected)) return;
        boolean first = detail == null;
        detail = payload;
        forms.show(payload);
        recipes.show(payload);
        if (first || tab != Tab.FORMS) rebuildWidgets();
    }

    void acceptFamily(RecipeFamilyPayload payload) {
        recipes.accept(payload);
    }

    /** Every suggestion of this material becomes a pending choice; choices already made are kept. */
    private void acceptMaterialSuggestions() {
        for (MaterialDetailPayload.FormView view : detail.forms()) {
            var f = view.resolved();
            boolean suggestion = f.source() == dev.drimoz.materialnexus.core.policy.PolicyPrecedence.DEFAULT
                    && f.canonical().isPresent() && !f.alternatives().isEmpty();
            if (suggestion && PendingChanges.get(detail.material(), view.form()).isEmpty()) {
                PendingChanges.set(detail.material(), view.form(), f.canonical().get());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (preview != null) preview.setMessage(Component.translatable("screen.materialnexus.preview_button", PendingChanges.size()));
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, 24, 0x88000000);
        g.drawString(font, title, 8, 8, 0xFFFFFF);
        if (page != null) {
            g.drawString(font, Component.translatable("screen.materialnexus.materials", page.totalMatches()), 8 + font.width(title) + 10, 8, 0xAAAAAA);
            int sideW = panelX - 10;
            g.drawCenteredString(font, Component.translatable("screen.materialnexus.page", page.page() + 1, page.pageCount()), sideW / 2, height - 17, 0xAAAAAA);
        }
        if (readOnly) g.drawString(font, Component.translatable("screen.materialnexus.read_only"), width - font.width(Component.translatable("screen.materialnexus.read_only")) - 8, 8, 0xFFAA00);
        g.fill(panelX - 5, 26, panelX - 4, height, 0x44FFFFFF);

        if (detail == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.select_material"), panelX, 30, 0x888888);
            return;
        }
        g.drawString(font, Names.material(detail.material()), panelX, 30, 0xFFFFFF);
        switch (tab) {
            case FORMS -> forms.render(g, font, mouseX, mouseY);
            case RECIPES -> recipes.render(g, font, mouseX, mouseY);
            case MISSING -> renderMissing(g);
        }
    }

    private void renderMissing(GuiGraphics g) {
        if (detail.missing().isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.missing_none"), panelX, panelY, 0x888888);
            return;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.missing_intro"), panelX, panelY, 0xAAAAAA);
        int y = panelY + 14;
        for (var r : detail.missing()) {
            MutableComponent line = Component.translatable("screen.materialnexus.relation", Names.form(r.from().name()), Names.form(r.to().name()));
            g.drawString(font, line, panelX + 6, y, 0xFFFF55);
            y += 12;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (detail == null || mouseX < panelX) return false;
        return switch (tab) {
            case FORMS -> forms.click(mouseX, mouseY);
            case RECIPES -> recipes.click(mouseX, mouseY);
            case MISSING -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseY >= panelY) {
            if (tab == Tab.FORMS) forms.scroll(scrollY);
            if (tab == Tab.RECIPES) recipes.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
