package dev.drimoz.materialnexus.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.datapack.EditableData;
import dev.drimoz.materialnexus.datapack.FormPatterns;
import dev.drimoz.materialnexus.network.DataListPayload;
import dev.drimoz.materialnexus.network.DataListRequest;
import dev.drimoz.materialnexus.network.DataReadPayload;
import dev.drimoz.materialnexus.network.DataReadRequest;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Data view of the main screen (MNX-059, was a separate screen since MNX-046). "Files": every forms / template /
 * format / preset / material file, grouped by kind, edited as JSON; an edit is saved as the player's version in the
 * edits pack, "Back to original" removes it. "Untagged forms": item names that look like a form of a known material,
 * each declarable as a form pattern in one click. Every edit is a pending change: Preview, then Apply.
 */
final class DataPanel {
    /** Patterns declared from the audit go to this file of the edits pack. */
    static final ResourceLocation DECLARED = ResourceLocation.fromNamespaceAndPath("materialnexus_user", "declared_patterns");
    private static final int ROW = 12;
    private static final int PAGE = 12;

    private record Row(EditableData.Kind kind, DataListPayload.Item item) { }

    private final Runnable rebuild;
    private final Ui.Hits hits = new Ui.Hits();
    private DataListPayload list;
    private boolean untagged;
    private String filter = "";
    private DataListPayload.Item selected;
    private String loadedText = "";
    /** Text typed in the editor and not kept yet: survives widget rebuilds. */
    private String draft;
    private boolean selectedEdited;
    private double scroll;
    private int untaggedPage;
    private EditableData.Kind newKind = EditableData.Kind.FORMS;
    private String newId = "materialnexus_user:custom";
    private JsonObject declared;
    private int x, y, w, h;

    DataPanel(Runnable rebuild) {
        this.rebuild = rebuild;
    }

    private static Button active(Button button, boolean active) {
        button.active = active;
        return button;
    }

    private int side() { return Math.clamp(w / 3, 170, 260); }

    // ---- widgets -------------------------------------------------------------------------------------------

    void init(Consumer<AbstractWidget> add, Font font, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (list == null) {
            PacketDistributor.sendToServer(DataListRequest.INSTANCE);
            return;
        }
        if (untagged) initUntagged(add, font);
        else initFiles(add, font);
    }

    private void initFiles(Consumer<AbstractWidget> add, Font font) {
        int side = side();
        EditBox search = new EditBox(font, x + 8, y + 64, side - 16, 16, Component.translatable("screen.materialnexus.search"));
        search.setHint(Component.translatable("screen.materialnexus.data.filter"));
        search.setValue(filter);
        search.setResponder(v -> { filter = v; scroll = 0; });
        add.accept(search);

        int by = y + h - 44;
        add.accept(Button.builder(Component.translatable("screen.materialnexus.data.kind." + newKind.key), b -> {
            var kinds = EditableData.Kind.values();
            newKind = kinds[(newKind.ordinal() + 1) % kinds.length];
            rebuild.run();
        }).bounds(x + 8, by, 96, 18).build());
        EditBox id = new EditBox(font, x + 108, by + 1, side - 116, 16, Component.empty());
        id.setValue(newId);
        id.setResponder(v -> newId = v);
        add.accept(id);
        add.accept(Button.builder(Component.translatable("screen.materialnexus.data.new"), b -> {
            ResourceLocation rl = ResourceLocation.tryParse(newId.strip().toLowerCase(Locale.ROOT));
            if (rl != null) select(new DataListPayload.Item(newKind.key, rl, EditableData.USER_PACK_ID, false));
        }).bounds(x + 8, by + 22, side - 16, 18).build());

        if (selected == null) return;
        int ex = x + side + 8;
        String key = selected.id().toString();
        MultiLineEditBox editor = new MultiLineEditBox(font, ex, y + 92, x + w - ex - 8, h - 92 - 28, Component.empty(), Component.empty());
        editor.setCharacterLimit(EditableData.MAX_TEXT);
        editor.setValue(draft != null ? draft : PendingChanges.data(selected.kind(), key).map(t -> t.orElse(loadedText)).orElse(loadedText));
        editor.setValueListener(v -> draft = v);
        add.accept(editor);

        int bx = ex;
        int bw = 130;
        add.accept(Button.builder(Component.translatable("screen.materialnexus.data.keep"), b -> {
            if (editor.getValue().equals(loadedText)) PendingChanges.clearData(selected.kind(), key);
            else PendingChanges.setData(selected.kind(), key, Optional.of(editor.getValue()));
            draft = null;
            rebuild.run();
        }).bounds(bx, y + h - 22, bw, 18).build());
        add.accept(active(Button.builder(Component.translatable("screen.materialnexus.data.discard"), b -> {
            PendingChanges.clearData(selected.kind(), key);
            draft = null;
            rebuild.run();
        }).bounds(bx + bw + 4, y + h - 22, bw, 18).build(), draft != null || PendingChanges.data(selected.kind(), key).isPresent()));
        Button original = Button.builder(Component.translatable("screen.materialnexus.data.original"), b -> {
            PendingChanges.setData(selected.kind(), key, Optional.empty());
            draft = null;
            rebuild.run();
        }).bounds(bx + 2 * (bw + 4), y + h - 22, bw, 18).build();
        original.active = selectedEdited;
        original.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.materialnexus.data.original.tooltip")));
        add.accept(original);
    }

    private void initUntagged(Consumer<AbstractWidget> add, Font font) {
        List<FormPatterns.Candidate> candidates = list.untagged();
        int pages = Math.max(1, (candidates.size() + PAGE - 1) / PAGE);
        untaggedPage = Math.clamp(untaggedPage, 0, pages - 1);
        int ry = y + 76;
        for (int i = untaggedPage * PAGE; i < Math.min(candidates.size(), (untaggedPage + 1) * PAGE); i++) {
            FormPatterns.Candidate c = candidates.get(i);
            EditBox form = new EditBox(font, x + w - 258, ry + 2, 120, 16, Component.empty());
            form.setValue(guessForm(c.pattern()));
            add.accept(form);
            boolean done = isDeclared(c.pattern());
            add.accept(active(Button.builder(Component.translatable(done ? "screen.materialnexus.data.declared" : "screen.materialnexus.data.declare"),
                    b -> declare(form.getValue().strip().toLowerCase(Locale.ROOT), c.pattern())).bounds(x + w - 132, ry + 1, 120, 18).build(), !done && declared != null));
            ry += 24;
        }
        add.accept(active(Button.builder(Component.literal("<"), b -> { untaggedPage--; rebuild.run(); }).bounds(x + 8, y + h - 22, 20, 18).build(), untaggedPage > 0));
        add.accept(active(Button.builder(Component.literal(">"), b -> { untaggedPage++; rebuild.run(); }).bounds(x + 32, y + h - 22, 20, 18).build(), untaggedPage < pages - 1));
    }

    // ---- responses -----------------------------------------------------------------------------------------

    private void select(DataListPayload.Item item) {
        selected = item;
        loadedText = "";
        draft = null;
        selectedEdited = item.edited();
        PacketDistributor.sendToServer(new DataReadRequest(item.kind(), item.id()));
        rebuild.run();
    }

    void acceptList(DataListPayload payload) {
        list = payload;
        rebuild.run();
    }

    void acceptRead(DataReadPayload payload) {
        if (payload.kind().equals(EditableData.Kind.FORMS.key) && payload.id().equals(DECLARED) && declared == null) {
            declared = PendingChanges.data(payload.kind(), DECLARED.toString()).flatMap(t -> t)
                    .or(payload::text)
                    .map(t -> JsonParser.parseString(t).getAsJsonObject())
                    .orElseGet(JsonObject::new);
            rebuild.run();
        }
        if (selected != null && payload.kind().equals(selected.kind()) && payload.id().equals(selected.id())) {
            loadedText = payload.text().orElse("{\n}");
            selectedEdited = payload.edited();
            rebuild.run();
        }
    }

    // ---- input ---------------------------------------------------------------------------------------------

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    boolean scroll(double mx, double my, double delta) {
        if (untagged || mx >= x + side()) return false;
        scroll = Math.max(0, scroll - delta * ROW * 3);
        return true;
    }

    // ---- render --------------------------------------------------------------------------------------------

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        g.drawString(font, Component.translatable("screen.materialnexus.data_title"), x + 12, y + 6, Ui.TEXT, false);
        int tx = x + 10;
        int ty = y + 20;
        for (boolean mode : new boolean[] {false, true}) {
            Component label = mode ? Component.translatable("screen.materialnexus.data.untagged_count", list == null ? 0 : list.untagged().size())
                    : Component.translatable("screen.materialnexus.data.files");
            int tw = font.width(label) + 12;
            boolean active = untagged == mode;
            boolean over = mx >= tx && mx < tx + tw && my >= ty && my < ty + 16;
            if (active) g.fill(tx, ty + 15, tx + tw, ty + 16, Ui.ACCENT);
            g.drawString(font, label, tx + 6, ty + 4, active ? Ui.TEXT : over ? 0xCCCCCC : Ui.MUTED, false);
            hits.add(tx, ty, tw, 16, () -> {
                untagged = mode;
                if (mode && declared == null) PacketDistributor.sendToServer(new DataReadRequest(EditableData.Kind.FORMS.key, DECLARED));
                rebuild.run();
            }, null, List.of());
            tx += tw + 2;
        }
        g.fill(x + 8, ty + 16, x + w - 8, ty + 17, Ui.LINE);
        Component intro = Component.translatable(untagged ? "screen.materialnexus.data.untagged_intro" : "screen.materialnexus.data.intro");
        g.drawString(font, font.plainSubstrByWidth(intro.getString(), w - 24), x + 12, y + 44, Ui.FAINT, false);
        if (list == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x + 12, y + 64, Ui.MUTED, false);
            return;
        }
        if (untagged) renderUntagged(g, font);
        else renderFiles(g, font, mx, my);
        hits.tooltip(g, font, mx, my);
    }

    private List<Row> rows() {
        String f = filter.strip().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        for (EditableData.Kind kind : EditableData.Kind.values()) {
            List<DataListPayload.Item> items = list.entries().stream().filter(e -> e.kind().equals(kind.key))
                    .filter(e -> f.isEmpty() || e.id().toString().contains(f)).sorted(Comparator.comparing(e -> e.id().toString())).toList();
            if (items.isEmpty()) continue;
            rows.add(new Row(kind, null));
            items.forEach(i -> rows.add(new Row(kind, i)));
        }
        return rows;
    }

    private void renderFiles(GuiGraphics g, Font font, int mx, int my) {
        int side = side();
        int top = y + 84;
        int bottom = y + h - 50;
        List<Row> rows = rows();
        scroll = Math.min(scroll, Math.max(0, rows.size() * ROW - (bottom - top)));
        g.fill(x + side, y + 60, x + side + 1, y + h, Ui.LINE);
        g.enableScissor(x, top, x + side, bottom);
        int ry = top - (int) scroll;
        for (Row row : rows) {
            if (ry + ROW > top && ry < bottom) {
                if (row.item() == null) {
                    long count = list.entries().stream().filter(e -> e.kind().equals(row.kind().key)).count();
                    g.drawString(font, Component.translatable("screen.materialnexus.data.kind." + row.kind().key).getString() + " (" + count + ")",
                            x + 10, ry + 2, Ui.MUTED, false);
                } else {
                    DataListPayload.Item item = row.item();
                    boolean isSelected = selected != null && selected.kind().equals(item.kind()) && selected.id().equals(item.id());
                    boolean over = my >= top && my < bottom && mx >= x + 4 && mx < x + side - 2 && my >= ry && my < ry + ROW;
                    if (isSelected) g.fill(x + 4, ry, x + side - 2, ry + ROW, (Ui.ACCENT & 0x00FFFFFF) | 0x50000000);
                    else if (over) g.fill(x + 4, ry, x + side - 2, ry + ROW, 0x18FFFFFF);
                    int dot = status(item) == 2 ? Ui.ACCENT : status(item) == 1 ? Ui.SUCCESS : 0;
                    if (dot != 0) g.fill(x + 10, ry + 4, x + 14, ry + 8, dot);
                    // The mod's own files are most of the list: their namespace is implied, others show it.
                    String label = item.id().getNamespace().equals(dev.drimoz.materialnexus.MaterialNexus.MOD_ID) ? item.id().getPath()
                            : item.id().getPath() + " · " + item.id().getNamespace();
                    g.drawString(font, font.plainSubstrByWidth(label, side - 30), x + 18, ry + 2, isSelected ? Ui.TEXT : 0xC8C8D0, false);
                    if (my >= top && my < bottom) {
                        hits.add(x + 4, ry, side - 6, ROW, () -> select(item), null,
                                List.of(Component.literal(item.id().toString()), Component.translatable("screen.materialnexus.data.source", item.source()).withColor(0x888888)));
                    }
                }
            }
            ry += ROW;
        }
        g.disableScissor();
        if (rows.isEmpty()) g.drawString(font, Component.translatable("screen.materialnexus.data.no_match"), x + 10, top + 2, Ui.FAINT, false);

        int ex = x + side + 10;
        if (selected == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.data.select"), ex, y + 66, Ui.MUTED, false);
            int ky = y + 84;
            for (EditableData.Kind kind : EditableData.Kind.values()) {
                g.fill(ex, ky, ex + 2, ky + 8, Ui.ACCENT);
                g.drawString(font, Component.translatable("screen.materialnexus.data.kind." + kind.key), ex + 6, ky, Ui.TEXT, false);
                ky += 11;
                for (var line : font.split(Component.translatable("screen.materialnexus.data.kind." + kind.key + ".desc"), x + w - ex - 16)) {
                    g.drawString(font, line, ex + 6, ky, Ui.FAINT, false);
                    ky += 10;
                }
                ky += 6;
            }
            return;
        }
        EditableData.Kind kind = EditableData.Kind.byKey(selected.kind()).orElse(EditableData.Kind.FORMS);
        Component title = Component.translatable("screen.materialnexus.data.kind." + kind.key).copy().append(" · " + selected.id());
        g.drawString(font, font.plainSubstrByWidth(title.getString(), x + w - ex - 120), ex, y + 64, Ui.TEXT, false);
        int s = status(selected);
        Ui.pill(g, font, Component.translatable(s == 2 ? "screen.materialnexus.data.status.pending" : s == 1 ? "screen.materialnexus.data.status.edited"
                : "screen.materialnexus.data.status.original"), x + w - 8 - pillWidth(font, s), y + 62, s == 2 ? Ui.ACCENT : s == 1 ? Ui.SUCCESS : Ui.NEUTRAL);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.data.kind." + kind.key + ".desc").getString(), x + w - ex - 8),
                ex, y + 78, Ui.FAINT, false);
    }

    private static int pillWidth(Font font, int status) {
        return font.width(Component.translatable(status == 2 ? "screen.materialnexus.data.status.pending" : status == 1 ? "screen.materialnexus.data.status.edited"
                : "screen.materialnexus.data.status.original")) + 8;
    }

    /** 2 pending edit, 1 the player's version in use, 0 original. */
    private int status(DataListPayload.Item item) {
        if (PendingChanges.data(item.kind(), item.id().toString()).isPresent()) return 2;
        boolean edited = selected != null && selected.kind().equals(item.kind()) && selected.id().equals(item.id()) ? selectedEdited : item.edited();
        return edited ? 1 : 0;
    }

    private void renderUntagged(GuiGraphics g, Font font) {
        List<FormPatterns.Candidate> candidates = list.untagged();
        if (candidates.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.data.untagged_none"), x + 12, y + 64, Ui.SUCCESS, false);
            return;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.data.col.pattern"), x + 12, y + 62, Ui.FAINT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.data.col.form"), x + w - 258, y + 62, Ui.FAINT, false);
        int ry = y + 76;
        for (int i = untaggedPage * PAGE; i < Math.min(candidates.size(), (untaggedPage + 1) * PAGE); i++) {
            FormPatterns.Candidate c = candidates.get(i);
            g.fill(x + 8, ry + 22, x + w - 8, ry + 23, Ui.LINE);
            g.drawString(font, font.plainSubstrByWidth(c.pattern(), w - 290), x + 12, ry + 2, Ui.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.data.materials", c.materials().size(),
                    String.join(", ", c.materials())).getString(), w - 290), x + 12, ry + 12, Ui.FAINT, false);
            ry += 24;
        }
        int pages = Math.max(1, (candidates.size() + PAGE - 1) / PAGE);
        g.drawString(font, (untaggedPage + 1) + " / " + pages, x + 60, y + h - 17, Ui.MUTED, false);
    }

    // ---- untagged forms ------------------------------------------------------------------------------------

    /** "ns:{material}_double_ingot" suggests double_ingot; the player can type any form name. */
    static String guessForm(String pattern) {
        int at = pattern.indexOf("{material}");
        String rest = pattern.substring(at + "{material}".length()).replaceFirst("^_", "");
        return rest.isEmpty() ? pattern.substring(pattern.indexOf(':') + 1, at).replaceAll("_$", "") : rest;
    }

    private boolean isDeclared(String pattern) {
        if (declared == null || !(declared.get("patterns") instanceof JsonObject patterns)) return false;
        return patterns.entrySet().stream().anyMatch(e -> e.getValue().isJsonArray()
                && e.getValue().getAsJsonArray().contains(new com.google.gson.JsonPrimitive(pattern)));
    }

    /** Adds the pattern to the pending declared-patterns file; a form no tag folder knows also gets one ("<form>s"). */
    private void declare(String form, String pattern) {
        if (declared == null || dev.drimoz.materialnexus.core.domain.FormId.read(form).result().isEmpty()) return;
        JsonObject patterns = declared.get("patterns") instanceof JsonObject p ? p : new JsonObject();
        JsonArray forForm = patterns.get(form) instanceof JsonArray a ? a : new JsonArray();
        forForm.add(pattern);
        patterns.add(form, forForm);
        declared.add("patterns", patterns);
        if (!list.forms().contains(form)) {
            JsonObject folders = declared.get("folders") instanceof JsonObject f ? f : new JsonObject();
            folders.addProperty(form + "s", form);
            declared.add("folders", folders);
        }
        PendingChanges.setData(EditableData.Kind.FORMS.key, DECLARED.toString(),
                Optional.of(new GsonBuilder().setPrettyPrinting().create().toJson(declared)));
        rebuild.run();
    }
}
