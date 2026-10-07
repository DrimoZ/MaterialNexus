package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialDetailRequest;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.MaterialListRequest;
import dev.drimoz.materialnexus.network.MatrixPayload;
import dev.drimoz.materialnexus.network.MatrixRequest;
import dev.drimoz.materialnexus.network.NexusQueries;
import dev.drimoz.materialnexus.network.PreviewPayload;
import dev.drimoz.materialnexus.network.PreviewRequest;
import dev.drimoz.materialnexus.network.ProcessPayload;
import dev.drimoz.materialnexus.network.ProcessRequest;
import dev.drimoz.materialnexus.network.RecipeFamilyPayload;
import dev.drimoz.materialnexus.network.RecipeFamilyRequest;
import dev.drimoz.materialnexus.network.RevertRequest;
import dev.drimoz.materialnexus.network.SuggestionsRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Material Nexus main screen (MNX-049). A navigation rail (Home, Materials, Forms, Data, Presets), a top bar with the
 * pack's status and a search, a bottom bar with the pending changes, and the Preview as a side panel. Materials: a
 * filtered list and the selected material as a table (forms, recipes, missing). Forms: the materials × forms grid,
 * and one form across every material (table or process rule). Everything comes from targeted server requests;
 * choices stay pending on the client until Preview / Apply.
 */
public final class NexusScreen extends Screen {
    private enum View { HOME, MATERIALS, FORMS, TRIAGE, PRESETS, DATA }
    private enum Tab { FORMS, RECIPES, MISSING, PROCESS }

    private static final int TOP = 24;
    private static final int BOTTOM = 24;
    private static final int NAV = 78;

    private final boolean readOnly;
    private final boolean canRevert;
    private final PresetPanel presets;
    private final DataPanel data = new DataPanel(() -> { if (this.view == View.DATA) rebuildWidgets(); });
    private final DetailTable table;
    private final RecipesPanel recipes;
    private final ProcessPanel process;
    private final FormMatrix matrix;
    private final TriagePanel triage;
    private boolean triageOnMatrix;
    private final Ui.Hits hits = new Ui.Hits();

    private View view = View.HOME;
    private Tab tab = Tab.FORMS;
    private String query = "";
    private int filter = NexusQueries.ALL;
    private MaterialListPayload page;
    private MaterialListPayload.Totals totals;
    private String material;
    private String form;
    private MaterialDetailPayload detail;
    private String recipesOnArrival;
    private MaterialSidebar list;
    private Drawer drawer;
    private int contentX, contentY, contentW, contentH;

    /** MNX-056: the latest applies and reverts, newest first (lines of history.jsonl). */
    private final List<com.google.gson.JsonObject> history = new java.util.ArrayList<>();

    public NexusScreen(boolean readOnly, boolean canRevert, java.util.Map<ResourceLocation, String> presets, String historyJson) {
        super(Component.translatable("screen.materialnexus.title"));
        this.readOnly = readOnly;
        this.canRevert = canRevert;
        this.presets = new PresetPanel(presets);
        try {
            com.google.gson.JsonParser.parseString(historyJson).getAsJsonArray().forEach(e -> history.add(e.getAsJsonObject()));
        } catch (RuntimeException ignored) {
            // Nothing to show.
        }
        this.table = new DetailTable(readOnly, this::openRecipes);
        this.recipes = new RecipesPanel(f -> PacketDistributor.sendToServer(new RecipeFamilyRequest(material, f)));
        this.process = new ProcessPanel(readOnly, this::rebuildWidgets);
        this.matrix = new FormMatrix(this::openMaterial, this::openForm);
        this.triage = new TriagePanel(() -> go(View.HOME));
    }

    boolean readOnly() { return readOnly; }

    void refresh() { rebuildWidgets(); }

    // ---- navigation ----------------------------------------------------------------------------------------

    private void go(View next) {
        view = next;
        drawer = null;
        if (next == View.FORMS && !matrix.loaded()) PacketDistributor.sendToServer(MatrixRequest.INSTANCE);
        if (next == View.MATERIALS && page == null) requestList();
        rebuildWidgets();
    }

    private void openMaterial(String name) {
        view = View.MATERIALS;
        if (tab == Tab.PROCESS) tab = Tab.FORMS;
        material = name;
        detail = null;
        PacketDistributor.sendToServer(new MaterialDetailRequest(name, false));
        if (page == null) requestList();
        rebuildWidgets();
    }

    private void openForm(String name) {
        view = View.FORMS;
        if (tab == Tab.RECIPES || tab == Tab.MISSING) tab = Tab.FORMS;
        form = name;
        detail = null;
        PacketDistributor.sendToServer(new MaterialDetailRequest(name, true));
        requestProcess();
        rebuildWidgets();
    }

    /** From a row: the Recipes tab of that material (from a form view, the material is opened first). */
    private void openRecipes(String materialName, String formName) {
        tab = Tab.RECIPES;
        if (view == View.MATERIALS && materialName.equals(material)) {
            rebuildWidgets();
            recipes.select(formName);
            return;
        }
        recipesOnArrival = formName;
        openMaterial(materialName);
        tab = Tab.RECIPES;
    }

    private void requestList() {
        PacketDistributor.sendToServer(new MaterialListRequest(0, query, false, filter));
    }

    private void requestProcess() {
        if (view == View.FORMS && tab == Tab.PROCESS && form != null && !process.shows(form)) {
            PacketDistributor.sendToServer(new ProcessRequest(form));
        }
    }

    private void preview() {
        PacketDistributor.sendToServer(PendingChanges.request(false, Optional.empty()));
    }

    /** Dev screenshots only ({@link UiShots}): drive the screen like a player would. */
    void devShow(String which, String arg) {
        switch (which) {
            case "material" -> openMaterial(arg);
            case "matrix" -> { form = null; go(View.FORMS); }
            case "form" -> openForm(arg);
            case "suggestions" -> PacketDistributor.sendToServer(SuggestionsRequest.INSTANCE);
            case "preview" -> preview();
            case "process" -> { tab = Tab.PROCESS; requestProcess(); rebuildWidgets(); }
            case "forms_tab" -> { tab = Tab.FORMS; rebuildWidgets(); }
            case "triage" -> { PendingChanges.clear(); startTriage(); }
            case "presets" -> go(View.PRESETS);
            case "data" -> go(View.DATA);
            case "pending" -> { PendingChanges.set("copper", "ingot", dev.drimoz.materialnexus.datapack.CanonicalChange.RESET); togglePending(); }
            case "priority" -> { if (totals != null) savePriority(new java.util.ArrayList<>(totals.mods().subList(0, Math.min(3, totals.mods().size())))); }
            default -> go(View.HOME);
        }
    }

    // ---- responses -----------------------------------------------------------------------------------------

    /** Responses can arrive out of order while typing; only the one matching the current filter is shown. */
    void acceptList(MaterialListPayload payload) {
        totals = payload.totals();
        if (payload.byForm() || !payload.query().equals(query.strip().toLowerCase(Locale.ROOT))) return;
        page = payload;
        if (list != null) list.show(payload.entries(), material, false);
        if (view == View.MATERIALS && material == null && !payload.entries().isEmpty()) openMaterial(payload.entries().getFirst().material());
    }

    /** MNX-053: the queue is built from the grid, which is requested first if needed. */
    private void startTriage() {
        view = View.TRIAGE;
        drawer = null;
        triageOnMatrix = true;
        PacketDistributor.sendToServer(MatrixRequest.INSTANCE);
        rebuildWidgets();
    }

    void acceptDetail(MaterialDetailPayload payload) {
        if (view == View.TRIAGE && !payload.byForm()) {
            triage.accept(payload);
            return;
        }
        String expected = payload.byForm() ? form : material;
        if (!payload.material().equals(expected) || payload.byForm() != (view == View.FORMS)) return;
        detail = payload;
        table.show(payload);
        if (!payload.byForm()) recipes.show(payload);
        else process.undecided((int) payload.forms().stream().filter(v -> DetailTable.status(v) == Ui.Status.SUGGESTION).count());
        rebuildWidgets();
        if (recipesOnArrival != null) {
            recipes.select(recipesOnArrival);
            recipesOnArrival = null;
        }
    }

    void acceptDataList(dev.drimoz.materialnexus.network.DataListPayload payload) {
        data.acceptList(payload);
    }

    void acceptDataRead(dev.drimoz.materialnexus.network.DataReadPayload payload) {
        data.acceptRead(payload);
    }

    void acceptMatrix(MatrixPayload payload) {
        matrix.accept(payload);
        if (triageOnMatrix) {
            triageOnMatrix = false;
            triage.start(payload);
        }
    }

    void acceptFamily(RecipeFamilyPayload payload) {
        recipes.accept(payload);
    }

    void acceptProcess(ProcessPayload payload) {
        if (view != View.FORMS || !payload.form().equals(form)) return;
        process.accept(payload);
        rebuildWidgets();
    }

    void showPreview(PreviewPayload payload) {
        drawer = new PreviewDrawer(payload, () -> {
            PacketDistributor.sendToServer(PendingChanges.request(true, payload.preset()));
            // The server reopens Material Nexus once the reload has finished; pending choices are kept until then.
            minecraft.setScreen(null);
        }, () -> drawer = null);
        layoutDrawer();
    }

    private void togglePending() {
        drawer = drawer instanceof PendingDrawer ? null : new PendingDrawer(this::preview, () -> drawer = null);
        layoutDrawer();
    }

    private void layoutDrawer() {
        if (drawer == null) return;
        int w = Math.max(220, width * 9 / 20);
        drawer.layout(width - w, TOP, w, height - TOP - BOTTOM);
    }

    // ---- widgets -------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        if (totals == null) requestList();
        EditBox search = new EditBox(font, width - 136, 4, 130, 16, Component.translatable("screen.materialnexus.search"));
        search.setMaxLength(NexusQueries.MAX_QUERY);
        search.setHint(Component.translatable("screen.materialnexus.search_everywhere"));
        search.setValue(query);
        search.setResponder(v -> {
            if (v.equals(query)) return;
            query = v;
            if (view != View.MATERIALS) { view = View.MATERIALS; rebuildWidgets(); }
            requestList();
        });
        addRenderableWidget(search);

        contentX = NAV + 1;
        contentY = TOP;
        contentW = width - contentX;
        contentH = height - TOP - BOTTOM;
        switch (view) {
            case HOME -> initHome();
            case MATERIALS -> initMaterials();
            case FORMS -> initForms();
            case TRIAGE, PRESETS -> { }
            case DATA -> data.init(widget -> addRenderableWidget(widget), font, contentX, contentY, contentW, contentH);
        }
        layoutDrawer();
    }

    private void initHome() {
        if (readOnly) return;
        int bx = contentX + 12;
        int by = contentY + 82;
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.unify_all"),
                b -> PacketDistributor.sendToServer(SuggestionsRequest.INSTANCE)).bounds(bx, by, 170, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.home.review"), b -> startTriage())
                .bounds(bx + 176, by, 170, 20).build());
        Button revert = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.revert"), b -> {
            PacketDistributor.sendToServer(RevertRequest.INSTANCE);
            minecraft.setScreen(null);
        }).bounds(bx, by + 24, 170, 20).build());
        revert.active = canRevert;
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.home.matrix"), b -> go(View.FORMS))
                .bounds(bx + 176, by + 24, 170, 20).build());
        // MNX-058: every saved choice and pack-wide setting back to default, as pending changes (Preview shows it all).
        Button reset = addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.reset.all"), b -> {
            PacketDistributor.sendToServer(SuggestionsRequest.SAVED);
            PendingChanges.resetSettings();
        }).bounds(bx, by + 48, 170, 20).build());
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.materialnexus.reset.all.tooltip")));
    }

    private int listWidth() { return Math.clamp(width / 5, 110, 170); }

    private void initMaterials() {
        int lw = listWidth();
        list = addRenderableWidget(new MaterialSidebar(minecraft, lw, contentH - 20, contentY + 20, this::openMaterial));
        list.setX(contentX);
        if (page != null) list.show(page.entries(), material, false);
        layoutDetail(contentX + lw + 6);
    }

    private void initForms() {
        if (form == null) {
            matrix.layout(contentX + 8, contentY + 8, contentW - 16, contentH - 12);
            return;
        }
        layoutDetail(contentX + 8);
        if (tab == Tab.PROCESS && process.shows(form)) process.init(this::addRenderableWidget);
    }

    private void layoutDetail(int dx) {
        int dw = width - dx - 6;
        int ty = contentY + 44;
        table.layout(dx, ty, dw, height - BOTTOM - ty - 2);
        recipes.layout(dx, ty, dw, height - BOTTOM - ty - 2);
        process.layout(dx, ty, dw, height - BOTTOM - ty - 2);
    }

    // ---- drawing -------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        g.fill(0, 0, width, height, Ui.BG);
        g.fill(0, 0, width, TOP, 0xFF16161C);
        g.fill(0, TOP - 1, width, TOP, Ui.LINE);
        g.fill(0, height - BOTTOM, width, height, 0xFF16161C);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, Ui.LINE);
        g.fill(NAV, TOP, NAV + 1, height - BOTTOM, Ui.LINE);
        renderTop(g);
        renderNav(g, mx, my);
        switch (view) {
            case HOME -> renderHome(g, mx, my);
            case MATERIALS -> renderMaterials(g, mx, my);
            case FORMS -> renderForms(g, mx, my);
            case TRIAGE -> triage.render(g, font, contentX + 16, contentY + 12, contentW - 32, contentH - 20, mx, my);
            case DATA -> data.render(g, font, mx, my);
            case PRESETS -> presets.render(g, font, contentX + 12, contentY + 10, contentW - 24, contentH - 20, mx, my);
        }
        renderBottom(g, mx, my);
        for (var widget : renderables) widget.render(g, mx, my, partialTick);
        if (drawer != null) {
            // Batched text (unicode glyphs such as ▲▼) would otherwise be drawn after the drawer's background.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            drawer.render(g, font, mx, my);
            g.pose().popPose();
        } else {
            hits.tooltip(g, font, mx, my);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) { }

    private void renderTop(GuiGraphics g) {
        g.drawString(font, title, 8, 8, Ui.TEXT, false);
        int px = 14 + font.width(title);
        if (totals != null) {
            px += 4 + Ui.pill(g, font, Component.translatable("screen.materialnexus.totals.todo", totals.toDecide()), px, 7, Ui.WARNING);
            px += 4 + Ui.pill(g, font, Component.translatable("screen.materialnexus.totals.unified", totals.unified()), px, 7, Ui.SUCCESS);
            px += 4 + Ui.pill(g, font, Component.translatable("screen.materialnexus.totals.aside", totals.setAside()), px, 7, Ui.NEUTRAL);
        }
        if (readOnly) Ui.pill(g, font, Component.translatable("screen.materialnexus.read_only"), px + 6, 7, Ui.DANGER);
    }

    private void renderNav(GuiGraphics g, int mx, int my) {
        int y = TOP + 6;
        y = navItem(g, mx, my, y, "home", view == View.HOME, () -> go(View.HOME));
        y = navItem(g, mx, my, y, "materials", view == View.MATERIALS, () -> go(View.MATERIALS));
        y = navItem(g, mx, my, y, "forms", view == View.FORMS, () -> { form = null; go(View.FORMS); });
        if (readOnly) return;
        y = navItem(g, mx, my, y, "data", view == View.DATA, () -> go(View.DATA));
        navItem(g, mx, my, y, "presets", view == View.PRESETS, () -> go(View.PRESETS));
    }

    private int navItem(GuiGraphics g, int mx, int my, int y, String key, boolean active, Runnable action) {
        boolean over = mx >= 4 && mx < NAV - 4 && my >= y && my < y + 16;
        if (active) g.fill(4, y, NAV - 4, y + 16, (Ui.ACCENT & 0x00FFFFFF) | 0x50000000);
        else if (over) g.fill(4, y, NAV - 4, y + 16, 0x18FFFFFF);
        if (active) g.fill(4, y, 6, y + 16, Ui.ACCENT);
        g.drawString(font, Component.translatable("screen.materialnexus.nav." + key), 10, y + 4, active ? Ui.TEXT : Ui.MUTED, false);
        hits.add(4, y, NAV - 8, 16, action, null, List.of());
        return y + 18;
    }

    private void renderBottom(GuiGraphics g, int mx, int my) {
        int y = height - BOTTOM + 8;
        if (readOnly) {
            g.drawString(font, Component.translatable("screen.materialnexus.read_only_hint"), 8, y, Ui.MUTED, false);
            return;
        }
        int pending = PendingChanges.size();
        if (pending == 0) {
            g.drawString(font, Component.translatable("screen.materialnexus.pending.none"), 8, y, Ui.FAINT, false);
        } else {
            // MNX-058: the summary opens the list of pending changes, where each one can be discarded.
            Component summary = Component.translatable("screen.materialnexus.pending.count", pending, PendingChanges.all().size(),
                    PendingChanges.processes().size(), PendingChanges.creations().size(), PendingChanges.data().size(),
                    PendingChanges.globalLines().size());
            Component review = Component.translatable("screen.materialnexus.pending.review");
            int sw = font.width(summary) + 12 + font.width(review);
            boolean over = mx >= 4 && mx < 20 + sw && my >= y - 4 && my < y + 12;
            if (over) g.fill(4, y - 4, 20 + sw, y + 12, 0x18FFFFFF);
            g.fill(8, y, 11, y + 8, Ui.ACCENT);
            g.drawString(font, summary, 16, y, Ui.TEXT, false);
            g.drawString(font, review, 28 + font.width(summary), y, over ? Ui.TEXT : 0x88AAFF, false);
            hits.add(4, y - 4, 16 + sw, 16, this::togglePending, null, List.of());
        }
        int bx = width - 8;
        bx = bottomButton(g, mx, my, bx, Component.translatable("screen.materialnexus.preview_short"), Ui.ACCENT, this::preview);
        bottomButton(g, mx, my, bx, Component.translatable("screen.materialnexus.discard_all"), Ui.NEUTRAL, pending == 0 ? null : () -> {
            PendingChanges.clear();
            drawer = null;
        });
    }

    private int bottomButton(GuiGraphics g, int mx, int my, int right, Component label, int color, Runnable action) {
        int w = font.width(label) + 16;
        int x = right - w;
        int y = height - BOTTOM + 4;
        boolean over = action != null && mx >= x && mx < right && my >= y && my < y + 16;
        g.fill(x, y, right, y + 16, (color & 0x00FFFFFF) | (action == null ? 0x30000000 : over ? 0xD0000000 : 0x90000000));
        g.drawCenteredString(font, label, x + w / 2, y + 4, action == null ? Ui.FAINT : Ui.TEXT);
        if (action != null) hits.add(x, y, w, 16, action, null, List.of());
        return x - 4;
    }

    private void renderHome(GuiGraphics g, int mx, int my) {
        int x = contentX + 12;
        int y = contentY + 10;
        g.drawString(font, Component.translatable("screen.materialnexus.home.title"), x, y, Ui.TEXT, false);
        if (totals != null) {
            int cw = 110;
            card(g, x, y + 16, cw, Component.translatable("screen.materialnexus.home.todo"), totals.toDecide(), Ui.WARNING);
            card(g, x + cw + 6, y + 16, cw, Component.translatable("screen.materialnexus.home.unified"), totals.unified(), Ui.SUCCESS);
            card(g, x + 2 * (cw + 6), y + 16, cw, Component.translatable("screen.materialnexus.home.aside"), totals.setAside(), Ui.NEUTRAL);
        }
        int ty = contentY + 164;
        boolean wide = contentW > 640;
        int tipWidth = wide ? 370 : contentW - 24;
        for (String key : List.of("home.tip1", "home.tip2", "home.tip3")) {
            for (var line : font.split(Component.translatable("screen.materialnexus." + key), tipWidth)) {
                g.drawString(font, line, x, ty, Ui.MUTED, false);
                ty += 11;
            }
            ty += 3;
        }
        ty = renderHistory(g, x, ty + 8, tipWidth, wide ? 12 : 4);
        renderModPriority(g, mx, my, wide ? contentX + 400 : x, wide ? contentY + 10 : ty + 12, wide ? Math.min(340, contentW - 410) : 300);
    }

    /**
     * MNX-051: the mods providing duplicates, ranked (first wins every suggestion it is part of) or not ranked yet.
     * Editing it is a pending change of the mod_priority field of global.json; Apply then unifies every form one of
     * them provides.
     */
    private void renderModPriority(GuiGraphics g, int mx, int my, int x, int y, int w) {
        if (totals == null) return;
        g.drawString(font, Component.translatable("screen.materialnexus.priority.title"), x, y, Ui.TEXT, false);
        // MNX-061: the priority has its own reset, separate from "Reset everything to default".
        if (!readOnly && PendingChanges.global("mod_priority") instanceof com.google.gson.JsonArray current && !current.isEmpty()) {
            Component reset = Component.translatable("screen.materialnexus.priority.reset");
            int rx = x + w - font.width(reset);
            boolean over = mx >= rx && mx < x + w && my >= y - 1 && my < y + 9;
            g.drawString(font, reset, rx, y, over ? Ui.TEXT : 0x88AAFF, false);
            hits.add(rx, y - 1, font.width(reset), 10, () -> PendingChanges.setGlobal("mod_priority", new com.google.gson.JsonArray()), null,
                    List.of(Component.translatable("screen.materialnexus.priority.reset.tooltip")));
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.priority.hint").getString(), w), x, y + 11, Ui.FAINT, false);
        List<String> ranked = new java.util.ArrayList<>();
        if (PendingChanges.global("mod_priority") instanceof com.google.gson.JsonArray a) a.forEach(e -> ranked.add(e.getAsString()));
        List<String> rest = totals.mods().stream().filter(m -> !ranked.contains(m)).toList();
        int ry = y + 26;
        int bottom = height - BOTTOM - 4;
        boolean pending = PendingChanges.globalChanged("mod_priority");
        for (int i = 0; i < ranked.size() && ry + 12 < bottom; i++) {
            String mod = ranked.get(i);
            g.fill(x, ry - 1, x + w, ry + 11, i % 2 == 0 ? 0x14FFFFFF : 0);
            g.drawString(font, (i + 1) + ". " + modName(mod), x + 4, ry + 1, pending ? 0x88BBFF : Ui.TEXT, false);
            int bx = x + w - 42;
            int index = i;
            if (!readOnly) {
                bx = smallButton(g, mx, my, bx, ry, "▲", () -> movePriority(ranked, index, -1));
                bx = smallButton(g, mx, my, bx, ry, "▼", () -> movePriority(ranked, index, 1));
                smallButton(g, mx, my, bx, ry, "×", () -> { ranked.remove(index); savePriority(ranked); });
            }
            ry += 12;
        }
        if (!rest.isEmpty() && ry + 24 < bottom) {
            g.drawString(font, Component.translatable("screen.materialnexus.priority.unranked"), x, ry + 4, Ui.FAINT, false);
            ry += 16;
        }
        for (String mod : rest) {
            if (ry + 12 >= bottom) break;
            g.drawString(font, modName(mod), x + 4, ry + 1, Ui.MUTED, false);
            if (!readOnly) smallButton(g, mx, my, x + w - 14, ry, "+", () -> { ranked.add(mod); savePriority(ranked); });
            ry += 12;
        }
    }

    /** MNX-056: one line per apply or revert, newest first; returns the y below the list. */
    private int renderHistory(GuiGraphics g, int x, int y, int w, int max) {
        g.drawString(font, Component.translatable("screen.materialnexus.history.title"), x, y, Ui.TEXT, false);
        y += 13;
        if (history.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.history.none"), x, y, Ui.FAINT, false);
            return y + 11;
        }
        var format = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (var entry : history.subList(0, Math.min(max, history.size()))) {
            if (y + 11 > height - BOTTOM - 4) break;
            String when;
            try {
                when = format.format(java.time.Instant.parse(entry.get("at").getAsString()));
            } catch (RuntimeException e) {
                when = "?";
            }
            boolean revert = entry.has("kind") && "revert".equals(entry.get("kind").getAsString());
            Component line = revert ? Component.translatable("screen.materialnexus.history.revert", when)
                    : Component.translatable("screen.materialnexus.history.apply", when, num(entry, "effects"), num(entry, "choices"),
                            num(entry, "rules"), num(entry, "items"), num(entry, "data"));
            g.fill(x, y, x + 2, y + 8, revert ? Ui.WARNING : Ui.SUCCESS);
            g.drawString(font, font.plainSubstrByWidth(line.getString(), w - 6), x + 6, y, Ui.MUTED, false);
            y += 11;
        }
        return y;
    }

    private static int num(com.google.gson.JsonObject entry, String field) {
        try {
            return entry.has(field) ? entry.get(field).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static String modName(String namespace) {
        return net.neoforged.fml.ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private int smallButton(GuiGraphics g, int mx, int my, int x, int y, String label, Runnable action) {
        boolean over = mx >= x && mx < x + 12 && my >= y - 1 && my < y + 11;
        g.fill(x, y - 1, x + 12, y + 11, over ? 0x60FFFFFF : 0x28FFFFFF);
        g.drawCenteredString(font, label, x + 6, y + 1, Ui.TEXT);
        hits.add(x, y - 1, 12, 12, action, null, List.of());
        return x + 14;
    }

    private void movePriority(List<String> ranked, int index, int delta) {
        int to = index + delta;
        if (to < 0 || to >= ranked.size()) return;
        java.util.Collections.swap(ranked, index, to);
        savePriority(ranked);
    }

    private void savePriority(List<String> ranked) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        ranked.forEach(array::add);
        PendingChanges.setGlobal("mod_priority", array);
    }

    private void card(GuiGraphics g, int x, int y, int w, Component label, int value, int color) {
        g.fill(x, y, x + w, y + 46, 0xFF1C1C24);
        g.fill(x, y, x + 2, y + 46, color);
        g.drawString(font, label, x + 8, y + 6, Ui.MUTED, false);
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 20, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, String.valueOf(value), 0, 0, Ui.TEXT, false);
        g.pose().popPose();
    }

    private void renderMaterials(GuiGraphics g, int mx, int my) {
        int lw = listWidth();
        int fx = contentX + 4;
        for (int value : new int[] {NexusQueries.ALL, NexusQueries.TO_DECIDE, NexusQueries.UNIFIED}) {
            String key = value == NexusQueries.TO_DECIDE ? "todo" : value == NexusQueries.UNIFIED ? "unified" : "all";
            Component label = Component.translatable("screen.materialnexus.filter." + key);
            int w = font.width(label) + 8;
            boolean active = filter == value;
            g.fill(fx, contentY + 5, fx + w, contentY + 16, active ? (Ui.ACCENT & 0x00FFFFFF) | 0x70000000 : 0x20FFFFFF);
            g.drawString(font, label, fx + 4, contentY + 7, active ? Ui.TEXT : Ui.MUTED, false);
            hits.add(fx, contentY + 5, w, 11, () -> { filter = value; requestList(); }, null, List.of());
            fx += w + 3;
        }
        g.fill(contentX + lw + 2, contentY, contentX + lw + 3, height - BOTTOM, Ui.LINE);
        int dx = contentX + lw + 6;
        if (material == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.select_material"), dx + 4, contentY + 10, Ui.MUTED, false);
            return;
        }
        renderDetailHeader(g, mx, my, dx, Names.material(material), detail);
        renderTabs(g, mx, my, dx, new Tab[] {Tab.FORMS, Tab.RECIPES, Tab.MISSING});
        renderTab(g, mx, my, dx);
    }

    private void renderForms(GuiGraphics g, int mx, int my) {
        if (form == null) {
            matrix.render(g, font, mx, my);
            return;
        }
        int dx = contentX + 8;
        renderDetailHeader(g, mx, my, dx, Names.form(form), detail);
        renderTabs(g, mx, my, dx, new Tab[] {Tab.FORMS, Tab.PROCESS});
        renderTab(g, mx, my, dx);
    }

    private void renderDetailHeader(GuiGraphics g, int mx, int my, int dx, Component name, MaterialDetailPayload d) {
        int x = dx + 2;
        if (view == View.FORMS) {
            Component back = Component.translatable("screen.materialnexus.all_forms");
            boolean over = mx >= x && mx < x + font.width(back) && my >= contentY + 4 && my < contentY + 14;
            g.drawString(font, back, x, contentY + 5, over ? Ui.TEXT : 0x88AAFF, false);
            hits.add(x, contentY + 4, font.width(back), 10, () -> { form = null; detail = null; rebuildWidgets(); }, null, List.of());
            x += font.width(back) + 10;
        }
        g.drawString(font, name, x, contentY + 5, Ui.TEXT, false);
        if (d == null) return;
        long todo = d.forms().stream().filter(v -> DetailTable.status(v) == Ui.Status.SUGGESTION).count();
        g.drawString(font, Component.translatable(d.byForm() ? "screen.materialnexus.header.form" : "screen.materialnexus.header.material",
                d.forms().size(), todo), x + font.width(name) + 8, contentY + 5, Ui.MUTED, false);
        if (readOnly) return;
        int ux = width - 8;
        // MNX-058: discard what is pending for this material (or this form) only.
        String m = d.byForm() ? null : d.material();
        String f = d.byForm() ? d.material() : null;
        int pending = PendingChanges.countFor(m, f);
        if (pending > 0) {
            Component discard = Component.translatable("screen.materialnexus.discard_here", pending);
            int dw = font.width(discard) + 12;
            ux -= dw;
            boolean overD = mx >= ux && mx < ux + dw && my >= contentY + 2 && my < contentY + 16;
            g.fill(ux, contentY + 2, ux + dw, contentY + 16, (Ui.NEUTRAL & 0x00FFFFFF) | (overD ? 0xF0000000 : 0x90000000));
            g.drawString(font, discard, ux + 6, contentY + 5, Ui.TEXT, false);
            hits.add(ux, contentY + 2, dw, 14, () -> PendingChanges.clearFor(m, f), null, List.of());
            ux -= 4;
        }
        // MNX-058: the saved choices of this material (or form) back to default.
        List<MaterialDetailPayload.FormView> saved = d.forms().stream()
                .filter(v -> (v.resolved().source() == dev.drimoz.materialnexus.core.policy.PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE
                        || v.resolved().ignoredOverride().isPresent()) && PendingChanges.get(v.material(), v.form()).isEmpty()).toList();
        if (!saved.isEmpty() && tab != Tab.PROCESS) {
            Component reset = Component.translatable("screen.materialnexus.reset.here", saved.size());
            int rw = font.width(reset) + 12;
            ux -= rw;
            boolean overR = mx >= ux && mx < ux + rw && my >= contentY + 2 && my < contentY + 16;
            g.fill(ux, contentY + 2, ux + rw, contentY + 16, (Ui.NEUTRAL & 0x00FFFFFF) | (overR ? 0xF0000000 : 0x90000000));
            g.drawString(font, reset, ux + 6, contentY + 5, Ui.TEXT, false);
            hits.add(ux, contentY + 2, rw, 14, () -> saved.forEach(v -> PendingChanges.set(v.material(), v.form(),
                    dev.drimoz.materialnexus.datapack.CanonicalChange.RESET)), null, List.of(Component.translatable("screen.materialnexus.reset.here.tooltip")));
            ux -= 4;
        }
        if (todo == 0 || tab == Tab.PROCESS) return;
        Component unify = Component.translatable(d.byForm() ? "screen.materialnexus.unify_form" : "screen.materialnexus.unify_material");
        int w = font.width(unify) + 12;
        ux -= w;
        boolean over = mx >= ux && mx < ux + w && my >= contentY + 2 && my < contentY + 16;
        g.fill(ux, contentY + 2, ux + w, contentY + 16, (Ui.WARNING & 0x00FFFFFF) | (over ? 0x90000000 : 0x50000000));
        g.drawString(font, unify, ux + 6, contentY + 5, Ui.TEXT, false);
        hits.add(ux, contentY + 2, w, 14, this::acceptSuggestions, null, List.of());
    }

    private void renderTabs(GuiGraphics g, int mx, int my, int dx, Tab[] tabs) {
        int tx = dx + 2;
        int ty = contentY + 22;
        for (Tab t : tabs) {
            Component label = Component.translatable("screen.materialnexus.tab." + t.name().toLowerCase(Locale.ROOT));
            int w = font.width(label) + 12;
            boolean active = tab == t;
            boolean over = mx >= tx && mx < tx + w && my >= ty && my < ty + 16;
            if (active) g.fill(tx, ty + 15, tx + w, ty + 16, Ui.ACCENT);
            g.drawString(font, label, tx + 6, ty + 4, active ? Ui.TEXT : over ? 0xCCCCCC : Ui.MUTED, false);
            hits.add(tx, ty, w, 16, () -> { tab = t; requestProcess(); rebuildWidgets(); }, null, List.of());
            tx += w + 2;
        }
        g.fill(dx, ty + 16, width - 6, ty + 17, Ui.LINE);
    }

    private void renderTab(GuiGraphics g, int mx, int my, int dx) {
        if (detail == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), dx + 4, contentY + 48, Ui.MUTED, false);
            return;
        }
        switch (tab) {
            case FORMS -> table.render(g, font, mx, my);
            case RECIPES -> recipes.render(g, font, mx, my);
            case MISSING -> renderMissing(g, dx);
            case PROCESS -> process.render(g, font);
        }
    }

    private void renderMissing(GuiGraphics g, int dx) {
        int y = contentY + 48;
        if (detail.missing().isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.missing_none"), dx + 4, y, Ui.MUTED, false);
            return;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.missing_intro"), dx + 4, y, Ui.MUTED, false);
        y += 14;
        for (var r : detail.missing()) {
            g.drawString(font, Component.translatable("screen.materialnexus.relation", Names.form(r.from().name()), Names.form(r.to().name())), dx + 10, y, Ui.WARNING, false);
            y += 12;
        }
    }

    /** Every suggestion shown (this material, or this form across materials) becomes a pending choice; choices already made are kept. */
    private void acceptSuggestions() {
        if (detail == null) return;
        for (MaterialDetailPayload.FormView v : detail.forms()) {
            if (DetailTable.status(v) == Ui.Status.SUGGESTION) PendingChanges.set(v.material(), v.form(), v.resolved().canonical().orElseThrow());
        }
    }

    // ---- input ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (drawer != null && drawer.contains(mx, my)) return drawer.click(mx, my, button);
        if (super.mouseClicked(mx, my, button)) return true;
        if (hits.click(mx, my, button)) return true;
        if (my < TOP || my > height - BOTTOM || mx < contentX) return false;
        if (view == View.TRIAGE) return triage.click(mx, my, button);
        if (view == View.PRESETS && presets.click(mx, my, button)) return true;
        if (view == View.DATA) return data.click(mx, my, button);
        if (view == View.FORMS && form == null) return matrix.click(mx, my, button);
        if (detail == null) return false;
        return switch (tab) {
            case FORMS -> table.click(mx, my, button);
            case RECIPES -> recipes.click(mx, my);
            case MISSING, PROCESS -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (drawer != null && drawer.contains(mx, my)) {
            drawer.scroll(scrollY);
            return true;
        }
        if (view == View.DATA && data.scroll(mx, my, scrollY)) return true;
        if (view == View.FORMS && form == null && mx >= contentX) {
            matrix.scroll(scrollY);
            return true;
        }
        if (my >= contentY + 44 && detail != null && mx >= contentX + (view == View.MATERIALS ? listWidth() : 0)) {
            switch (tab) {
                case FORMS -> table.scroll(scrollY);
                case RECIPES -> recipes.scroll(scrollY);
                case PROCESS -> { if (process.scroll(scrollY)) rebuildWidgets(); }
                case MISSING -> { }
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && drawer != null) {
            drawer = null;
            return true;
        }
        if (view == View.TRIAGE && drawer == null && !(getFocused() instanceof EditBox) && triage.key(key)) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
