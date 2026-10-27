package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Btn;
import dev.drimoz.materialnexus.client.ui.Field;
import dev.drimoz.materialnexus.client.ui.Regions;
import dev.drimoz.materialnexus.client.ui.ScreenSettings;
import dev.drimoz.materialnexus.client.ui.Theme;
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
import dev.drimoz.materialnexus.network.PacketDistributor;

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
    private enum View { HOME, MATERIALS, FORMS, TRIAGE, PRESETS, DATA, SCRIPTS, SETTINGS, SHORTCUTS }
    private enum Tab { FORMS, RECIPES, MISSING, PROCESS }

    private static final int TOP = Theme.BAR_TOP;
    private static final int BOTTOM = Theme.BAR_BOTTOM;
    /** Width of the navigation rail, from {@link Regions}. */
    private int nav = 104;

    private final boolean readOnly;
    private final boolean canRevert;
    private final PresetPanel presets;
    private final DataPanel data = new DataPanel(() -> { if (this.view == View.DATA) rebuildWidgets(); });
    private final DetailTable table;
    private final RecipesPanel recipes;
    private final ProcessPanel process;
    private final FormMatrix matrix;
    private final TriagePanel triage;
    private final ScriptsPanel scripts;
    private boolean scriptsRequested;
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
    private final MaterialSidebar list = new MaterialSidebar(this::openMaterial);
    private ReviewDrawer drawer;
    private int contentX, contentY, contentW, contentH;

    /** MNX-056: the latest applies and reverts, newest first (lines of history.jsonl). */
    private final List<com.google.gson.JsonObject> history = new java.util.ArrayList<>();

    public NexusScreen(boolean readOnly, boolean canRevert, java.util.Map<ResourceLocation, String> presets, String historyJson) {
        super(Component.translatable("screen.materialnexus.title"));
        ScreenSettings.load(dev.drimoz.materialnexus.datapack.MnxPaths.root());
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
        this.process = new ProcessPanel(readOnly);
        this.matrix = new FormMatrix(this::openMaterial, this::openForm);
        this.triage = new TriagePanel(() -> go(View.HOME), this::preview);
        this.scripts = new ScriptsPanel(readOnly);
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
        list.select(name);
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

    /** MNX-097: one side panel in two steps, what was asked and what it does; {@code effects} opens it on the second. */
    private void review(boolean effects) {
        drawer = new ReviewDrawer(effects, () -> PacketDistributor.sendToServer(PendingChanges.request(false, Optional.empty())), preset -> {
            PacketDistributor.sendToServer(PendingChanges.request(true, preset));
            // The server reopens Material Nexus once the reload has finished; pending choices are kept until then.
            // MNX-102: until then this screen stays and says what is going on.
            applying = net.minecraft.Util.getMillis();
            drawer = null;
        }, () -> drawer = null);
        layoutDrawer();
    }

    private void preview() { review(true); }

    /** Dev screenshots only ({@link UiShots}): drive the screen like a player would. */
    /** Captures only (UiShots): where the pointer is said to be, to show what a hover draws. */
    private int[] devMouse;

    void devShow(String which, String arg) {
        switch (which) {
            case "click" -> {
                int[] at = java.util.Arrays.stream(arg.split(",")).mapToInt(Integer::parseInt).toArray();
                mouseClicked(at[0], at[1], 0);
            }
            case "mouse" -> devMouse = arg.isEmpty() ? null : java.util.Arrays.stream(arg.split(",")).mapToInt(Integer::parseInt).toArray();
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
            case "scripts" -> go(View.SCRIPTS);
            case "settings" -> go(View.SETTINGS);
            case "shortcuts" -> go(View.SHORTCUTS);
            case "smooth" -> { ScreenSettings.set(dev.drimoz.materialnexus.datapack.MnxPaths.root(), ScreenSettings.get().withSmoothFont(true)); rebuildWidgets(); }
            case "recipes" -> { if (material != null) openRecipes(material, arg); }
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
        list.show(payload.entries(), material);
        if (view == View.MATERIALS && material == null && !payload.entries().isEmpty()) openMaterial(payload.entries().get(0).material());
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

    void acceptScripts(dev.drimoz.materialnexus.network.ScriptsPayload payload) {
        scripts.accept(payload);
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

    /** The effects the server worked out: shown in the review panel if it is still on that step. */
    void showPreview(PreviewPayload payload) {
        if (drawer != null && drawer.onEffects()) drawer.accept(payload);
    }

    private void togglePending() {
        if (drawer != null && !drawer.onEffects()) drawer = null;
        else review(false);
    }

    private void layoutDrawer() {
        if (drawer == null) return;
        int w = Math.max(220, width * 9 / 20);
        drawer.layout(width - w, TOP, w, height - TOP - BOTTOM);
    }

    // ---- widgets -------------------------------------------------------------------------------------------

    /** Smallest room the layout works in, in GUI pixels (MNX-075). */
    static final int MIN_WIDTH = 640;
    static final int MIN_HEIGHT = 360;

    /**
     * MNX-075: the player's GUI scale can leave too little room (the default 854x480 window at Auto is 427x240), so this
     * screen takes the largest scale that still gives {@link #MIN_WIDTH} x {@link #MIN_HEIGHT}, never larger than the
     * player's. Runs on every init, which a window resize also triggers after the game has put the player's scale back.
     */
    private void fitScale() {
        var window = minecraft.getWindow();
        int scale = window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
        while (scale > 1 && (window.getWidth() / scale < MIN_WIDTH || window.getHeight() / scale < MIN_HEIGHT)) scale--;
        if (scale == window.getGuiScale()) return;
        window.setGuiScale(scale);
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();
    }

    /** Back to the player's scale before the next screen (or the HUD) lays itself out. */
    @Override
    public void removed() {
        // MNX-101: where the player was, for the next opening.
        lastView = view == View.TRIAGE ? View.HOME : view;
        lastMaterial = material;
        lastForm = form;
        var window = minecraft.getWindow();
        window.setGuiScale(window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
        super.removed();
    }

    @Override
    protected void init() {
        fitScale();
        // MNX-103: every text of the screen is drawn and measured with the font the player chose.
        font = dev.drimoz.materialnexus.client.ui.UiFont.current();
        if (!restored) {
            restored = true;
            restore();
        }
        if (totals == null) requestList();
        if (!scriptsRequested) {
            scriptsRequested = true;
            PacketDistributor.sendToServer(dev.drimoz.materialnexus.network.ScriptsRequest.INSTANCE);
        }
        Regions regions = Regions.of(width);
        nav = regions.rail();
        int searchWidth = width >= Regions.WIDE ? 200 : 132;
        EditBox search = new Field(font, width - 8 - searchWidth, 3, searchWidth, Theme.CONTROL, Component.translatable("screen.materialnexus.search"));
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

        contentX = nav + 1;
        contentY = TOP;
        contentW = width - contentX;
        contentH = height - TOP - BOTTOM;
        switch (view) {
            case HOME -> initHome();
            case MATERIALS -> initMaterials();
            case FORMS -> initForms();
            case TRIAGE, PRESETS, SCRIPTS, SETTINGS, SHORTCUTS -> { }
            case DATA -> data.init(widget -> addRenderableWidget(widget), font, contentX, contentY, contentW, contentH);
        }
        layoutDrawer();
    }

    /** MNX-096: the Overview draws its own buttons, so that only one of them is the primary one. */
    private void initHome() { }

    private int listWidth() { return Regions.of(width).list(); }

    private void initMaterials() {
        int lw = listWidth();
        list.layout(contentX, contentY + 28, lw, contentH - 28);
        layoutDetail(contentX + lw + 6);
    }

    private void initForms() {
        if (form == null) {
            matrix.layout(contentX + 8, contentY + 8, contentW - 16, contentH - 12);
            return;
        }
        layoutDetail(contentX + 8);
    }

    private void layoutDetail(int dx) {
        int dw = width - dx - 6;
        int ty = contentY + 44;
        // MNX-095: the table and, beside it, the inspector of the selected row; on a narrow screen the inspector goes over.
        Regions regions = Regions.of(width);
        int th = height - BOTTOM - ty;
        int iw = regions.inspector();
        int tw = regions.inspectorOverlay() ? dw : Math.min(640, width - dx - iw);
        table.layout(dx, ty, tw, th, regions.inspectorOverlay() ? width - iw : dx + tw, ty, iw, th, regions.inspectorOverlay());
        recipes.layout(dx, ty, dw, height - BOTTOM - ty - 2);
        process.layout(dx, ty, dw, height - BOTTOM - ty - 2);
    }

    // ---- drawing -------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (devMouse == null) draw(g, mx, my, partialTick);
        else draw(g, devMouse[0], devMouse[1], partialTick);
    }

    private void draw(GuiGraphics g, int pointerX, int pointerY, float partialTick) {
        hits.clear();
        // MNX-119: what lies under the drawer gets no pointer while it is over the drawer, so no hover or tooltip of
        // a row beneath is drawn over it. The drawer itself gets the real one.
        boolean overDrawer = drawer != null && drawer.contains(pointerX, pointerY);
        int mx = overDrawer ? -1 : pointerX;
        int my = overDrawer ? -1 : pointerY;
        g.fill(0, 0, width, height, Theme.BG);
        g.fill(0, 0, width, TOP, Theme.SURFACE);
        g.fill(0, TOP - 1, width, TOP, Theme.BORDER);
        g.fill(0, height - BOTTOM, width, height, Theme.SURFACE);
        g.fill(0, height - BOTTOM, width, height - BOTTOM + 1, Theme.BORDER);
        g.fill(0, TOP, nav, height - BOTTOM, Theme.SURFACE);
        g.fill(nav, TOP, nav + 1, height - BOTTOM, Theme.BORDER);
        renderTop(g);
        renderNav(g, mx, my);
        switch (view) {
            case HOME -> renderHome(g, mx, my);
            case MATERIALS -> renderMaterials(g, mx, my);
            case FORMS -> renderForms(g, mx, my);
            case TRIAGE -> triage.render(g, font, contentX + 16, contentY + 12, contentW - 32, contentH - 20, mx, my);
            case DATA -> data.render(g, font, mx, my);
            case PRESETS -> presets.render(g, font, contentX + 12, contentY + 10, contentW - 24, contentH - 20, mx, my);
            case SCRIPTS -> scripts.render(g, font, contentX + 12, contentY + 10, contentW - 24, contentH - 20, mx, my);
            case SETTINGS -> renderSettings(g, mx, my);
            case SHORTCUTS -> renderShortcuts(g);
        }
        renderBottom(g, mx, my);
        for (var widget : renderables) widget.render(g, mx, my, partialTick);
        if (drawer != null) {
            // Batched text (unicode glyphs such as ▲▼) would otherwise be drawn after the drawer's background.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            drawer.render(g, font, pointerX, pointerY);
            g.pose().popPose();
        } else {
            hits.tooltip(g, font, mx, my);
        }
        if (applying > 0) renderApplying(g, pointerX, pointerY);
    }

    @Override
    public void renderBackground(GuiGraphics g) { }

    /** Where the player is, as a path. The pack's counters live in the Overview and as a badge of the rail. */
    private void renderTop(GuiGraphics g) {
        String here = switch (view) {
            case MATERIALS -> material == null ? null : Names.material(material).getString();
            case FORMS -> form == null ? null : Names.form(form).getString();
            case TRIAGE -> Component.translatable("screen.materialnexus.triage.title").getString();
            default -> null;
        };
        int x = crumb(g, title.getString(), 8, false);
        x = crumb(g, Component.translatable("screen.materialnexus.nav." + navKey(view)).getString(), x, here == null);
        if (here != null) x = crumb(g, here, x, true);
        if (readOnly) g.drawString(font, Component.translatable("screen.materialnexus.read_only"), x + 8, 8, Theme.WARN, false);
    }

    private int crumb(GuiGraphics g, String text, int x, boolean last) {
        g.drawString(font, text, x, 8, last ? Theme.TEXT : Theme.MUTED, false);
        x += font.width(text);
        if (last) return x;
        g.drawString(font, " › ", x, 8, Theme.FAINT, false);
        return x + font.width(" › ");
    }

    private static String navKey(View view) {
        return switch (view) {
            case HOME, TRIAGE -> "home";
            case MATERIALS -> "materials";
            case FORMS -> "forms";
            case SCRIPTS -> "scripts";
            case DATA -> "data";
            case PRESETS -> "presets";
            case SETTINGS -> "settings";
            case SHORTCUTS -> "shortcuts";
        };
    }

    private void renderNav(GuiGraphics g, int mx, int my) {
        int y = TOP + 8;
        y = navItem(g, mx, my, y, "home", view == View.HOME || view == View.TRIAGE, 0, () -> go(View.HOME));
        y = navItem(g, mx, my, y, "materials", view == View.MATERIALS, totals == null ? 0 : totals.toDecide(), () -> go(View.MATERIALS));
        y = navItem(g, mx, my, y, "forms", view == View.FORMS, 0, () -> { form = null; go(View.FORMS); });
        // MNX-076: only when scripts decide something; read-only servers see it too.
        if (!scripts.isEmpty()) y = navItem(g, mx, my, y, "scripts", view == View.SCRIPTS, scripts.differing(), () -> go(View.SCRIPTS));
        if (!readOnly) {
            g.drawString(font, Component.translatable("screen.materialnexus.nav.advanced"), 12, y + 12, Theme.FAINT, false);
            y += 24;
            y = navItem(g, mx, my, y, "data", view == View.DATA, 0, () -> go(View.DATA));
            navItem(g, mx, my, y, "presets", view == View.PRESETS, 0, () -> go(View.PRESETS));
        }
        // The foot of the rail: where to learn more, then the settings of the screen.
        int foot = height - BOTTOM - 8 - Theme.CONTROL;
        navItem(g, mx, my, foot - 40, "help", false, 0, () -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmLinkScreen(ok -> {
            if (ok) net.minecraft.Util.getPlatform().openUri(WIKI);
            minecraft.setScreen(this);
        }, WIKI, true)));
        navItem(g, mx, my, foot - 20, "shortcuts", view == View.SHORTCUTS, 0, () -> go(View.SHORTCUTS));
        navItem(g, mx, my, foot, "settings", view == View.SETTINGS, 0, () -> go(View.SETTINGS));
    }

    /** One entry of the rail; a {@code count} above zero is shown as a badge. Returns the y of the next entry. */
    private int navItem(GuiGraphics g, int mx, int my, int y, String key, boolean active, int count, Runnable action) {
        int h = Theme.CONTROL;
        boolean over = mx >= 4 && mx < nav - 4 && my >= y && my < y + h;
        if (active) g.fill(4, y, nav - 4, y + h, Theme.SEL);
        else if (over) g.fill(4, y, nav - 4, y + h, Theme.RAISED);
        g.drawString(font, Component.translatable("screen.materialnexus.nav." + key), 12, y + 5, active || over ? Theme.TEXT : Theme.MUTED, false);
        if (count > 0) {
            String text = String.valueOf(count);
            int w = font.width(text) + 8;
            g.fill(nav - 8 - w, y + 3, nav - 8, y + h - 3, active ? Theme.BG : Theme.RAISED);
            g.drawString(font, text, nav - 4 - w, y + 5, Theme.TEXT, false);
        }
        hits.add(4, y, nav - 8, h, action, null, List.of());
        return y + h + 2;
    }

    private void renderBottom(GuiGraphics g, int mx, int my) {
        int y = height - BOTTOM + 10;
        if (readOnly) {
            g.drawString(font, Component.translatable("screen.materialnexus.read_only_hint"), 8, y, Theme.MUTED, false);
            return;
        }
        int pending = PendingChanges.size();
        if (pending == 0) {
            g.drawString(font, Component.translatable("screen.materialnexus.pending.none"), 8, y, Theme.FAINT, false);
        } else {
            // MNX-058: the summary opens the list of pending changes, where each one can be discarded.
            Component summary = Component.translatable("screen.materialnexus.pending.count", pending, PendingChanges.all().size(),
                    PendingChanges.processes().size(), PendingChanges.creations().size(), PendingChanges.data().size(),
                    PendingChanges.globalLines().size());
            // A ring: chosen, not applied yet.
            g.renderOutline(8, y, 8, 8, Theme.INFO);
            g.drawString(font, summary, 22, y, Theme.TEXT, false);
            bottomButton(g, mx, my, 30 + font.width(summary), Component.translatable("screen.materialnexus.pending.review"), false, this::togglePending);
        }
        // The one permanent primary action of the screen, with the way back next to it.
        Component apply = Component.translatable("screen.materialnexus.preview_short");
        Component discard = Component.translatable("screen.materialnexus.discard_all");
        int ax = width - 8 - font.width(apply) - 16;
        bottomButton(g, mx, my, ax, apply, pending > 0 && drawer == null, this::preview);
        bottomButton(g, mx, my, ax - 4 - font.width(discard) - 16, discard, false, pending == 0 ? null : () -> {
            PendingChanges.clear();
            drawer = null;
        });
    }

    private void bottomButton(GuiGraphics g, int mx, int my, int x, Component label, boolean primary, Runnable action) {
        int w = font.width(label) + 16;
        int y = height - BOTTOM + 5;
        boolean over = action != null && mx >= x && mx < x + w && my >= y && my < y + Theme.CONTROL;
        Ui.button(g, font, label, x, y, w, Theme.CONTROL, primary, over, action != null);
        if (action != null) hits.add(x, y, w, Theme.CONTROL, action, null, List.of());
    }

    private static final String WIKI = "https://github.com/DrimoZ/MaterialNexus/wiki";
    /** When Apply was sent, 0 otherwise. */
    private long applying;
    private int closeX, closeY, closeW;

    /** MNX-102: applying reloads the game data, which takes a while on a large pack: said here, over the screen. */
    private void renderApplying(GuiGraphics g, int mx, int my) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(0, TOP, width, height, Theme.BG & 0x00FFFFFF | 0xB0000000);
        int w = 320;
        int x = (width - w) / 2;
        int y = (height - 76) / 2;
        g.fill(x, y, x + w, y + 76, Theme.SURFACE);
        g.renderOutline(x, y, w, 76, Theme.CTRL);
        g.drawString(font, tr("applying.title").copy().withStyle(net.minecraft.ChatFormatting.BOLD), x + 12, y + 10, Theme.TEXT, false);
        var lines = font.split(tr("applying.text"), w - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x + 12, y + 24 + i * 10, Theme.MUTED, false);
        int at = (int) ((net.minecraft.Util.getMillis() - applying) / 6 % (w - 24 + 60)) - 60;
        g.fill(x + 12, y + 48, x + w - 12, y + 50, Theme.RAISED);
        g.fill(Math.max(x + 12, x + 12 + at), y + 48, Math.min(x + w - 12, x + 12 + at + 60), y + 50, Theme.ACCENT);
        Component close = tr("applying.close");
        closeW = font.width(close) + 12;
        closeX = x + w - 12 - closeW;
        closeY = y + 54;
        Ui.button(g, font, close, closeX, closeY, closeW, 16, false, mx >= closeX && mx < closeX + closeW && my >= closeY && my < closeY + 16, true);
        g.pose().popPose();
    }

    /** MNX-102: every gesture that has no button of its own, in one place. */
    private void renderShortcuts(GuiGraphics g) {
        int x = contentX + 24;
        int y = contentY + 12;
        g.drawString(font, tr("shortcuts.title").copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        g.drawString(font, tr("shortcuts.hint"), x, y + 12, Theme.MUTED, false);
        y += 34;
        for (int i = 1; i <= 8; i++) {
            g.fill(x, y, x + Math.min(560, contentW - 48), y + 18, i % 2 == 1 ? Theme.SURFACE : Theme.BG);
            g.drawString(font, tr("shortcuts." + i + ".keys"), x + 8, y + 5, Theme.TEXT, false);
            g.drawString(font, tr("shortcuts." + i + ".what"), x + 200, y + 5, Theme.MUTED, false);
            y += 18;
        }
    }

    /** Where the screen was left, kept while the game runs (MNX-101). */
    private static View lastView;
    private static String lastMaterial, lastForm;
    private boolean restored;

    /** Reopens where the player was, when that setting is on; after an apply the Overview and its summary come first. */
    private void restore() {
        if (!ScreenSettings.get().reopen() || lastView == null || lastView == View.HOME) return;
        if (!history.isEmpty() && justApplied(history.get(0))) return;
        if (lastView == View.MATERIALS && lastMaterial != null) openMaterial(lastMaterial);
        else if (lastView == View.FORMS && lastForm != null) openForm(lastForm);
        else if ((lastView == View.DATA || lastView == View.PRESETS) && readOnly) return;
        else go(lastView);
    }

    private int settingToggle(GuiGraphics g, int x, int y, boolean on, String key, java.util.function.UnaryOperator<ScreenSettings.Values> change) {
        Ui.toggle(g, x, y + 1, on);
        Component label = tr("settings." + key);
        g.drawString(font, label, x + 28, y + 2, Theme.TEXT, false);
        hits.add(x, y - 2, 32 + font.width(label), 16, () -> ScreenSettings.set(dev.drimoz.materialnexus.datapack.MnxPaths.root(), change.apply(ScreenSettings.get())), null, List.of());
        return y + 18;
    }

    /** MNX-094: the player's own settings of this screen; they change nothing in the pack and apply at once. */
    private void renderSettings(GuiGraphics g, int mx, int my) {
        int x = contentX + 24;
        int y = contentY + 12;
        g.drawString(font, Component.translatable("screen.materialnexus.settings.title"), x, y, Theme.TEXT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.settings.hint"), x, y + 12, Theme.MUTED, false);
        y += 36;
        g.drawString(font, Component.translatable("screen.materialnexus.settings.theme"), x, y, Theme.MUTED, false);
        y += 14;
        for (Theme theme : Theme.values()) {
            boolean active = theme == Theme.current();
            boolean over = mx >= x && mx < x + 132 && my >= y && my < y + 58;
            g.fill(x, y, x + 132, y + 58, active ? Theme.SEL : over ? Theme.RAISED : Theme.SURFACE);
            if (active) g.renderOutline(x, y, 132, 58, Theme.INFO);
            // A sample of the theme itself: its ground, text levels, meanings and slot well.
            g.fill(x + 4, y + 4, x + 128, y + 40, theme.bg);
            g.drawString(font, "Copper Dust", x + 10, y + 9, theme.text, false);
            g.drawString(font, "Mekanism", x + 10, y + 22, theme.muted, false);
            g.fill(x + 84, y + 12, x + 92, y + 20, theme.warn);
            g.fill(x + 96, y + 12, x + 104, y + 20, theme.ok);
            g.fill(x + 108, y + 10, x + 120, y + 22, theme.well);
            g.drawString(font, Component.translatable("screen.materialnexus.theme." + theme.key()), x + 6, y + 46, active ? Theme.TEXT : Theme.MUTED, false);
            hits.add(x, y, 132, 58, () -> ScreenSettings.setTheme(dev.drimoz.materialnexus.datapack.MnxPaths.root(), theme), null, List.of());
            x += 140;
        }
        // MNX-101: what the screen shows and how it opens.
        x = contentX + 24;
        y += 76;
        g.drawString(font, tr("settings.font"), x, y, Theme.MUTED, false);
        y += 14;
        boolean smoothNow = ScreenSettings.get().smoothFont();
        int fx2 = x;
        for (boolean value : new boolean[] {false, true}) {
            Component label = tr(value ? "settings.font.smooth" : "settings.font.game");
            int bw = font.width(label) + 16;
            boolean over = mx >= fx2 && mx < fx2 + bw && my >= y && my < y + 18;
            g.fill(fx2, y, fx2 + bw, y + 18, value == smoothNow ? Theme.SEL : over ? Theme.RAISED : Theme.SURFACE);
            g.drawString(font, label, fx2 + 8, y + 5, value == smoothNow || over ? Theme.TEXT : Theme.MUTED, false);
            hits.add(fx2, y, bw, 18, () -> {
                ScreenSettings.set(dev.drimoz.materialnexus.datapack.MnxPaths.root(), ScreenSettings.get().withSmoothFont(value));
                rebuildWidgets();
            }, null, List.of());
            fx2 += bw;
        }
        y += 34;
        g.drawString(font, tr("settings.density"), x, y, Theme.MUTED, false);
        y += 14;
        boolean roomy = ScreenSettings.get().comfortable();
        int sx = x;
        for (boolean value : new boolean[] {false, true}) {
            Component label = tr(value ? "settings.density.comfortable" : "settings.density.compact");
            int bw = font.width(label) + 16;
            boolean over = mx >= sx && mx < sx + bw && my >= y && my < y + 18;
            g.fill(sx, y, sx + bw, y + 18, value == roomy ? Theme.SEL : over ? Theme.RAISED : Theme.SURFACE);
            g.drawString(font, label, sx + 8, y + 5, value == roomy || over ? Theme.TEXT : Theme.MUTED, false);
            hits.add(sx, y, bw, 18, () -> ScreenSettings.set(dev.drimoz.materialnexus.datapack.MnxPaths.root(), ScreenSettings.get().withComfortable(value)), null, List.of());
            sx += bw;
        }
        y += 34;
        // MNX-107: the soft templates are a resource pack of the mod; this is the same switch as the game's pack screen.
        var packs = minecraft.getResourcePackRepository();
        String softPack = packs.getAvailableIds().stream().filter(id -> id.endsWith("soft_templates")).findFirst().orElse(null);
        if (softPack != null) {
            g.drawString(font, tr("settings.templates"), x, y, Theme.MUTED, false);
            y += 14;
            boolean softNow = packs.getSelectedIds().contains(softPack);
            int tx = x;
            for (boolean value : new boolean[] {false, true}) {
                Component label = tr(value ? "settings.templates.soft" : "settings.templates.contrasted");
                int bw = font.width(label) + 16;
                boolean over = mx >= tx && mx < tx + bw && my >= y && my < y + 18;
                g.fill(tx, y, tx + bw, y + 18, value == softNow ? Theme.SEL : over ? Theme.RAISED : Theme.SURFACE);
                g.drawString(font, label, tx + 8, y + 5, value == softNow || over ? Theme.TEXT : Theme.MUTED, false);
                hits.add(tx, y, bw, 18, () -> {
                    // Saves the choice with the game's options and reloads the textures.
                    if (value ? packs.addPack(softPack) : packs.removePack(softPack)) minecraft.options.updateResourcePacks(packs);
                }, null, List.of());
                tx += bw;
            }
            y += 34;
        }
        g.drawString(font, tr("settings.display"), x, y, Theme.MUTED, false);
        y += 16;
        ScreenSettings.Values now = ScreenSettings.get();
        y = settingToggle(g, x, y, now.showIds(), "show_ids", v -> v.withShowIds(!v.showIds()));
        y = settingToggle(g, x, y, now.openGroups(), "open_groups", v -> v.withOpenGroups(!v.openGroups()));
        settingToggle(g, x, y, now.reopen(), "reopen", v -> v.withReopen(!v.reopen()));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus." + key, args);
    }

    /**
     * MNX-096, docs/22 section 6.2: where the pack stands in one sentence, the one thing to do next, three figures that
     * open the filtered list, then the mod priority and the history. What cannot be taken back sits apart, confirmed.
     */
    private void renderHome(GuiGraphics g, int mx, int my) {
        int x = contentX + 24;
        int y = contentY + 16;
        if (totals == null) {
            g.drawString(font, tr("process.loading"), x, y, Theme.MUTED, false);
            return;
        }
        boolean wide = contentW >= 760;
        int lw = Math.min(640, contentW - 48 - (wide ? 364 : 0));
        int todo = totals.toDecide();
        int chosen = totals.unified() - totals.byPriority();

        Component state = todo > 0 ? tr("home.state.todo", todo, todo + totals.unified()) : tr("home.state.done", totals.unified());
        g.drawString(font, state.copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        g.drawString(font, tr("home.state.sub", chosen, totals.byPriority()), x, y + 12, Theme.MUTED, false);
        y += 30;

        int bx = x;
        if (!readOnly && todo > 0) {
            // The primary action of the view, unless changes wait to be reviewed: then the bottom bar has it.
            boolean resume = chosen > 0 || PendingChanges.size() > 0;
            bx = homeButton(g, mx, my, bx, y, tr(resume ? "home.sort.resume" : "home.sort.start", todo), PendingChanges.size() == 0, this::startTriage);
            bx = homeButton(g, mx, my, bx, y, tr("unify_all"), false, () -> PacketDistributor.sendToServer(SuggestionsRequest.INSTANCE));
        }
        homeButton(g, mx, my, bx, y, tr("home.matrix"), false, () -> { form = null; go(View.FORMS); });
        y += 26;
        g.drawString(font, font.plainSubstrByWidth(tr("home.steps").getString(), lw), x, y, Theme.FAINT, false);
        y += 18;

        int fw = (lw - 16) / 3;
        // No warning mark on a zero: nothing is waiting.
        figure(g, mx, my, x, y, fw, todo > 0 ? Ui.Status.SUGGESTION : Ui.Status.NOTHING, tr("home.todo"), todo, Component.empty(), NexusQueries.TO_DECIDE);
        figure(g, mx, my, x + fw + 8, y, fw, Ui.Status.UNIFIED, tr("home.unified"), totals.unified(),
                tr("home.unified.split", chosen, totals.byPriority()), NexusQueries.UNIFIED);
        figure(g, mx, my, x + 2 * (fw + 8), y, fw, Ui.Status.NOTHING, tr("home.aside"), totals.setAside(), tr("home.aside.hint"), NexusQueries.ALL);
        y += 62;

        // MNX-097: what an apply did is said here, in the screen, when it has just happened; created items need a restart.
        if (!history.isEmpty() && justApplied(history.get(0))) {
            int items = num(history.get(0), "items");
            int h = items > 0 ? 32 : 20;
            g.fill(x, y, x + lw, y + h, Theme.RAISED);
            Ui.mark(g, Ui.Status.UNIFIED, x + 8, y + 7);
            g.drawString(font, tr("home.applied", num(history.get(0), "effects")), x + 20, y + 6, Theme.TEXT, false);
            if (items > 0) {
                Ui.mark(g, Ui.Status.SUGGESTION, x + 8, y + 19);
                g.drawString(font, tr("home.applied.restart", items), x + 20, y + 18, Theme.WARN, false);
            }
            y += h + 16;
        }

        if (scripts.decided() > 0) {
            // MNX-076: a pack that unified with scripts sees it first, before anything is applied.
            var lines = font.split(tr("home.scripts", scripts.decided(), scripts.differing()), lw - 24);
            int h = lines.size() * 10 + 8;
            boolean over = mx >= x && mx < x + lw && my >= y && my < y + h;
            g.fill(x, y, x + lw, y + h, over ? Theme.SEL : Theme.RAISED);
            Ui.mark(g, scripts.differing() > 0 ? Ui.Status.SUGGESTION : Ui.Status.UNIFIED, x + 8, y + 5);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 20, y + 4 + i * 10, Theme.TEXT, false);
            hits.add(x, y, lw, h, () -> go(View.SCRIPTS), null, List.of());
            y += h + 16;
        }

        int dangerTop = height - BOTTOM - (readOnly ? 0 : 60);
        if (wide) {
            renderHistory(g, mx, my, x, y, lw, dangerTop - 8);
            renderModPriority(g, mx, my, x + lw + 24, contentY + 16, Math.min(340, contentW - lw - 72), height - BOTTOM - 8);
        } else {
            int mid = y + Math.max(48, (dangerTop - y) / 3);
            renderHistory(g, mx, my, x, y, lw, mid - 8);
            renderModPriority(g, mx, my, x, mid, lw, dangerTop - 8);
        }
        if (readOnly) return;

        // What cannot be taken back: apart from the rest, in the danger colour, each behind a confirmation.
        g.fill(x, dangerTop, x + lw, dangerTop + 1, Theme.BORDER);
        g.drawString(font, tr("home.danger"), x, dangerTop + 8, Theme.MUTED, false);
        dangerButton(g, mx, my, x, dangerTop + 20, tr("revert"), tr("home.danger.revert"), canRevert, () -> confirm(tr("revert.confirm"), tr("revert.confirm_hint"), () -> {
            PacketDistributor.sendToServer(RevertRequest.INSTANCE);
            minecraft.setScreen(null);
        }));
        // MNX-058: every saved choice and pack-wide setting back to default, as pending changes (Review shows it all).
        dangerButton(g, mx, my, x, dangerTop + 38, tr("reset.all"), tr("home.danger.reset"), true, () -> confirm(tr("reset.confirm"), tr("reset.all.tooltip"), () -> {
            PacketDistributor.sendToServer(SuggestionsRequest.SAVED);
            PendingChanges.resetSettings();
            minecraft.setScreen(this);
        }));
    }

    private void confirm(Component title, Component hint, Runnable yes) {
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok -> {
            if (ok) yes.run();
            else minecraft.setScreen(this);
        }, title, hint));
    }

    private int homeButton(GuiGraphics g, int mx, int my, int x, int y, Component label, boolean primary, Runnable action) {
        int w = font.width(label) + 16;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + Theme.CONTROL;
        Ui.button(g, font, label, x, y, w, Theme.CONTROL, primary, over, true);
        hits.add(x, y, w, Theme.CONTROL, action, null, List.of());
        return x + w + 8;
    }

    private void dangerButton(GuiGraphics g, int mx, int my, int x, int y, Component label, Component consequence, boolean active, Runnable action) {
        int w = font.width(label) + 12;
        boolean over = active && mx >= x && mx < x + w && my >= y && my < y + 16;
        if (over) g.fill(x, y, x + w, y + 16, Theme.RAISED);
        g.drawString(font, label, x + 6, y + 4, active ? Theme.BAD : Theme.FAINT, false);
        g.drawString(font, font.plainSubstrByWidth(consequence.getString(), contentX + contentW - x - w - 32), x + w + 8, y + 4, Theme.FAINT, false);
        if (active) hits.add(x, y, w, 16, action, null, List.of());
    }

    /** One figure of the pack: its mark, the number, what it counts; a click opens the material list filtered on it. */
    private void figure(GuiGraphics g, int mx, int my, int x, int y, int w, Ui.Status status, Component label, int value, Component sub, int listFilter) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 46;
        g.fill(x, y, x + w, y + 46, over ? Theme.RAISED : Theme.SURFACE);
        g.drawString(font, font.plainSubstrByWidth(label.getString(), w - 16), x + 8, y + 6, Theme.MUTED, false);
        Ui.mark(g, status, x + 8, y + 20);
        int nx = status == Ui.Status.NOTHING ? x + 8 : x + 20;
        g.drawString(font, Component.literal(String.valueOf(value)).withStyle(net.minecraft.ChatFormatting.BOLD), nx, y + 19, Theme.TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(sub.getString(), w - 16), x + 8, y + 32, Theme.FAINT, false);
        hits.add(x, y, w, 46, () -> {
            filter = listFilter;
            go(View.MATERIALS);
            requestList();
        }, null, List.of());
    }

    /**
     * MNX-051: the mods providing duplicates, ranked (first wins every suggestion it is part of) or not ranked yet.
     * Editing it is a pending change of the mod_priority field of global.json; Apply then unifies every form one of
     * them provides.
     */
    private void renderModPriority(GuiGraphics g, int mx, int my, int x, int y, int w, int bottom) {
        g.drawString(font, tr("priority.title").copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        List<String> ranked = new java.util.ArrayList<>();
        if (PendingChanges.global("mod_priority") instanceof com.google.gson.JsonArray a) a.forEach(e -> ranked.add(e.getAsString()));
        // MNX-061: the priority has its own reset, separate from "Reset everything to default".
        if (!readOnly && !ranked.isEmpty()) {
            Component reset = tr("priority.reset");
            int rw = font.width(reset) + 12;
            boolean over = mx >= x + w - rw && mx < x + w && my >= y - 4 && my < y + 12;
            Ui.button(g, font, reset, x + w - rw, y - 4, rw, 16, false, over, true);
            hits.add(x + w - rw, y - 4, rw, 16, () -> PendingChanges.setGlobal("mod_priority", new com.google.gson.JsonArray()), null,
                    List.of(tr("priority.reset.tooltip")));
        }
        int ry = y + 12;
        for (var line : font.split(tr("priority.hint"), w)) {
            g.drawString(font, line, x, ry, Theme.MUTED, false);
            ry += 10;
        }
        ry += 6;
        List<String> rest = totals.mods().stream().filter(m -> !ranked.contains(m)).toList();
        boolean pending = PendingChanges.globalChanged("mod_priority");
        for (int i = 0; i < ranked.size() && ry + 18 <= bottom; i++) {
            int index = i;
            g.fill(x, ry, x + w, ry + 18, Theme.SURFACE);
            if (pending) g.renderOutline(x + 6, ry + 6, 6, 6, Theme.INFO);
            g.drawString(font, (i + 1) + ".", x + (pending ? 18 : 6), ry + 5, Theme.MUTED, false);
            g.drawString(font, font.plainSubstrByWidth(modName(ranked.get(i)), w - 130), x + (pending ? 34 : 22), ry + 5, Theme.TEXT, false);
            if (!readOnly) {
                Component remove = tr("priority.remove");
                int bx = x + w - font.width(remove) - 14;
                rowButton(g, mx, my, bx, ry + 1, remove, () -> { ranked.remove(index); savePriority(ranked); });
                rowButton(g, mx, my, bx - 20, ry + 1, Component.literal("▼"), () -> movePriority(ranked, index, 1));
                rowButton(g, mx, my, bx - 40, ry + 1, Component.literal("▲"), () -> movePriority(ranked, index, -1));
            }
            ry += 20;
        }
        if (!rest.isEmpty() && ry + 30 <= bottom) {
            g.drawString(font, tr("priority.unranked"), x, ry + 6, Theme.MUTED, false);
            ry += 20;
        }
        for (String mod : rest) {
            if (ry + 18 > bottom) break;
            g.drawString(font, font.plainSubstrByWidth(modName(mod), w - 60), x + 6, ry + 5, Theme.MUTED, false);
            if (!readOnly) {
                Component add = tr("priority.add");
                rowButton(g, mx, my, x + w - font.width(add) - 14, ry + 1, add, () -> { ranked.add(mod); savePriority(ranked); });
            }
            ry += 18;
        }
    }

    /** A 16 px button of a list row: an arrow or a word, at least 16 wide. */
    private void rowButton(GuiGraphics g, int mx, int my, int x, int y, Component label, Runnable action) {
        int w = Math.max(16, font.width(label) + 12);
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 16;
        Ui.button(g, font, label, x, y, w, 16, false, over, true);
        hits.add(x, y, w, 16, action, null, List.of());
    }

    /** MNX-056: one line per apply or revert, newest first, down to {@code bottom}. */
    private void renderHistory(GuiGraphics g, int mx, int my, int x, int y, int w, int bottom) {
        g.drawString(font, tr("history.title").copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        y += 14;
        if (history.isEmpty()) {
            g.drawString(font, tr("history.none"), x, y, Theme.FAINT, false);
            return;
        }
        var format = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneId.systemDefault());
        for (int i = 0; i < history.size() && y + 18 <= bottom; i++) {
            var entry = history.get(i);
            String when = when(format, entry, "at");
            String kind = entry.has("kind") ? entry.get("kind").getAsString() : "apply";
            Component line = switch (kind) {
                case "revert" -> tr("history.revert", when);
                case "restore" -> tr("history.restore", when, when(format, entry, "from"));
                default -> tr("history.apply", when, num(entry, "effects"), num(entry, "choices"), num(entry, "rules"), num(entry, "items"), num(entry, "data"));
            };
            // MNX-064: every older entry whose copy is kept can be restored; the newest one is the current state.
            boolean restorable = i > 0 && !readOnly && entry.has("snapshot");
            Component side = i == 0 ? tr("history.current") : tr("history.restore.button");
            int sw = font.width(side) + 12;
            g.drawString(font, font.plainSubstrByWidth(line.getString(), w - sw - 12), x, y + 5, i == 0 ? Theme.TEXT : Theme.MUTED, false);
            if (i == 0) {
                g.drawString(font, side, x + w - sw + 6, y + 5, Theme.FAINT, false);
            } else if (restorable) {
                String id = entry.get("snapshot").getAsString();
                rowButton(g, mx, my, x + w - sw, y + 1, side, () -> confirm(tr("history.restore.confirm", when), tr("history.restore.confirm_hint"), () -> {
                    PacketDistributor.sendToServer(new dev.drimoz.materialnexus.network.RestoreRequest(id));
                    minecraft.setScreen(null);
                }));
            }
            y += 18;
        }
    }

    /** An apply (not a revert or a restore) made in the last two minutes. */
    private static boolean justApplied(com.google.gson.JsonObject entry) {
        try {
            boolean apply = !entry.has("kind") || entry.get("kind").getAsString().equals("apply");
            return apply && java.time.Duration.between(java.time.Instant.parse(entry.get("at").getAsString()), java.time.Instant.now()).toSeconds() < 120;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String when(java.time.format.DateTimeFormatter format, com.google.gson.JsonObject entry, String field) {
        try {
            return format.format(java.time.Instant.parse(entry.get(field).getAsString()));
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static int num(com.google.gson.JsonObject entry, String field) {
        try {
            return entry.has(field) ? entry.get(field).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static String modName(String namespace) {
        return net.minecraftforge.fml.ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
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

    private void renderMaterials(GuiGraphics g, int mx, int my) {
        int lw = listWidth();
        int fx = contentX + 8;
        int fw = (lw - 16) / 3;
        for (int value : new int[] {NexusQueries.ALL, NexusQueries.TO_DECIDE, NexusQueries.UNIFIED}) {
            String key = value == NexusQueries.TO_DECIDE ? "todo" : value == NexusQueries.UNIFIED ? "unified" : "all";
            Component label = Component.translatable("screen.materialnexus.filter." + key);
            boolean active = filter == value;
            String text = font.plainSubstrByWidth(label.getString(), fw - 4);
            g.fill(fx, contentY + 5, fx + fw, contentY + 5 + Theme.CONTROL, active ? Theme.SEL : Theme.RAISED);
            g.drawString(font, text, fx + (fw - font.width(text)) / 2, contentY + 10, active ? Theme.TEXT : Theme.MUTED, false);
            hits.add(fx, contentY + 5, fw, Theme.CONTROL, () -> { filter = value; requestList(); }, null, List.of());
            fx += fw;
        }
        list.render(g, font, mx, my);
        g.fill(contentX + lw + 2, contentY, contentX + lw + 3, height - BOTTOM, Theme.BORDER);
        int dx = contentX + lw + 6;
        if (material == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.select_material"), dx + 4, contentY + 10, Theme.MUTED, false);
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
            g.drawString(font, back, x, contentY + 5, over ? Theme.TEXT : Theme.INFO, false);
            hits.add(x, contentY + 4, font.width(back), 10, () -> { form = null; detail = null; rebuildWidgets(); }, null, List.of());
            x += font.width(back) + 10;
        }
        g.drawString(font, name, x, contentY + 5, Theme.TEXT, false);
        if (d == null) return;
        long todo = d.forms().stream().filter(v -> DetailTable.status(v) == Ui.Status.SUGGESTION).count();
        g.drawString(font, Component.translatable(d.byForm() ? "screen.materialnexus.header.form" : "screen.materialnexus.header.material",
                d.forms().size(), todo), x + font.width(name) + 8, contentY + 5, Theme.MUTED, false);
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
            g.fill(ux, contentY + 2, ux + dw, contentY + 16, overD ? Theme.SEL : Theme.RAISED);
            g.drawString(font, discard, ux + 6, contentY + 5, Theme.TEXT, false);
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
            g.fill(ux, contentY + 2, ux + rw, contentY + 16, overR ? Theme.SEL : Theme.RAISED);
            g.drawString(font, reset, ux + 6, contentY + 5, Theme.TEXT, false);
            hits.add(ux, contentY + 2, rw, 14, () -> saved.forEach(v -> PendingChanges.set(v.material(), v.form(),
                    dev.drimoz.materialnexus.datapack.CanonicalChange.RESET)), null, List.of(Component.translatable("screen.materialnexus.reset.here.tooltip")));
            ux -= 4;
        }
        if (todo == 0 || tab == Tab.PROCESS) return;
        Component unify = Component.translatable(d.byForm() ? "screen.materialnexus.unify_form" : "screen.materialnexus.unify_material");
        int w = font.width(unify) + 12;
        ux -= w;
        boolean over = mx >= ux && mx < ux + w && my >= contentY + 2 && my < contentY + 16;
        g.fill(ux, contentY + 2, ux + w, contentY + 16, over ? Theme.SEL : Theme.RAISED);
        g.drawString(font, unify, ux + 6, contentY + 5, Theme.TEXT, false);
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
            if (active) g.fill(tx, ty + 14, tx + w, ty + 16, Theme.ACCENT);
            g.drawString(font, label, tx + 6, ty + 4, active ? Theme.TEXT : over ? Theme.MUTED : Theme.MUTED, false);
            hits.add(tx, ty, w, 16, () -> { tab = t; requestProcess(); rebuildWidgets(); }, null, List.of());
            tx += w + 2;
        }
        g.fill(dx, ty + 16, width - 6, ty + 17, Theme.BORDER);
    }

    private void renderTab(GuiGraphics g, int mx, int my, int dx) {
        if (detail == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), dx + 4, contentY + 48, Theme.MUTED, false);
            return;
        }
        switch (tab) {
            case FORMS -> table.render(g, font, mx, my);
            case RECIPES -> recipes.render(g, font, mx, my);
            case MISSING -> renderMissing(g, dx);
            case PROCESS -> process.render(g, font, mx, my);
        }
    }

    private void renderMissing(GuiGraphics g, int dx) {
        int y = contentY + 48;
        if (detail.missing().isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.missing_none"), dx + 4, y, Theme.MUTED, false);
            return;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.missing_intro"), dx + 4, y, Theme.MUTED, false);
        y += 14;
        for (var r : detail.missing()) {
            g.drawString(font, Component.translatable("screen.materialnexus.relation", Names.form(r.from().name()), Names.form(r.to().name())), dx + 10, y, Theme.WARN, false);
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
        if (applying > 0) {
            // Nothing else answers while the pack is being applied; Close leaves the screen, the apply goes on.
            if (mx >= closeX && mx < closeX + closeW && my >= closeY && my < closeY + 16) minecraft.setScreen(null);
            return true;
        }
        if (drawer != null && drawer.contains(mx, my)) return drawer.click(mx, my, button);
        if (super.mouseClicked(mx, my, button)) return true;
        if (hits.click(mx, my, button)) return true;
        if (my < TOP || my > height - BOTTOM || mx < contentX) return false;
        if (view == View.MATERIALS && list.click(mx, my, button)) return true;
        if (view == View.TRIAGE) return triage.click(mx, my, button);
        if (view == View.PRESETS && presets.click(mx, my, button)) return true;
        if (view == View.SCRIPTS) return scripts.click(mx, my, button);
        if (view == View.DATA) return data.click(mx, my, button);
        if (view == View.FORMS && form == null) return matrix.click(mx, my, button);
        if (detail == null) return false;
        return switch (tab) {
            case FORMS -> table.click(mx, my, button);
            case RECIPES -> recipes.click(mx, my);
            case PROCESS -> process.click(mx, my, button);
            case MISSING -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (drawer != null && drawer.contains(mx, my)) {
            drawer.scroll(scrollY);
            return true;
        }
        if (view == View.MATERIALS && list.scroll(mx, my, scrollY)) return true;
        if (view == View.DATA && data.scroll(mx, my, scrollY)) return true;
        if (view == View.SCRIPTS && mx >= contentX) {
            scripts.scroll(scrollY);
            return true;
        }
        if (view == View.FORMS && form == null && mx >= contentX) {
            matrix.scroll(scrollY);
            return true;
        }
        if (my >= contentY + 44 && detail != null && mx >= contentX + (view == View.MATERIALS ? listWidth() : 0)) {
            switch (tab) {
                case FORMS -> table.scroll(mx, my, scrollY);
                case RECIPES -> recipes.scroll(scrollY);
                case PROCESS -> process.scroll(scrollY);
                case MISSING -> { }
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollY);
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
