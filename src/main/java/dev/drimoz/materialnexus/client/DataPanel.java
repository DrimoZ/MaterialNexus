package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Btn;
import dev.drimoz.materialnexus.client.ui.Field;
import dev.drimoz.materialnexus.client.ui.Theme;
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

    /** MNX-102: as many untagged forms as the height shows, instead of a fixed twelve. */
    private int page() { return Math.max(4, (h - 76 - 52) / 24); }

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
        EditBox search = new Field(font, x + 8, y + 64, side - 16, 16, Component.translatable("screen.materialnexus.search"));
        search.setHint(Component.translatable("screen.materialnexus.data.filter"));
        search.setValue(filter);
        search.setResponder(v -> { filter = v; scroll = 0; });
        add.accept(search);

        int by = y + h - 44;
        add.accept(Btn.of(Component.translatable("screen.materialnexus.data.kind." + newKind.key), b -> {
            var kinds = EditableData.Kind.values();
            newKind = kinds[(newKind.ordinal() + 1) % kinds.length];
            rebuild.run();
        }).bounds(x + 8, by, 96, 18).build());
        EditBox id = new Field(font, x + 108, by + 1, side - 116, 16, Component.empty());
        id.setValue(newId);
        id.setResponder(v -> newId = v);
        add.accept(id);
        add.accept(Btn.of(Component.translatable("screen.materialnexus.data.new"), b -> {
            ResourceLocation rl = ResourceLocation.tryParse(newId.strip().toLowerCase(Locale.ROOT));
            if (rl != null) select(new DataListPayload.Item(newKind.key, rl, EditableData.USER_PACK_ID, false));
        }).bounds(x + 8, by + 22, side - 16, 18).build());

        if (selected == null) return;
        int ex = x + side + 8;
        String key = selected.id().toString();
        // MNX-100: capped in width (a wide JSON editor is mostly empty), with room under it for what is wrong with the text.
        MultiLineEditBox editor = new dev.drimoz.materialnexus.client.ui.Editor(font, ex, y + 92, Math.min(x + w - ex - 8, 600), h - 92 - 42);
        editor.setCharacterLimit(EditableData.MAX_TEXT);
        editor.setValue(draft != null ? draft : PendingChanges.data(selected.kind(), key).map(t -> t.orElse(loadedText)).orElse(loadedText));
        add.accept(editor);

        int bx = ex;
        int bw = 130;
        Button keep = Btn.of(Component.translatable("screen.materialnexus.data.keep"), b -> {
            if (editor.getValue().equals(loadedText)) PendingChanges.clearData(selected.kind(), key);
            else PendingChanges.setData(selected.kind(), key, Optional.of(editor.getValue()));
            draft = null;
            rebuild.run();
        }).bounds(bx, y + h - 22, bw, 18).build();
        // Text that does not read as JSON cannot be kept: the reason is written under the editor.
        keep.active = dev.drimoz.materialnexus.client.ui.Editor.jsonError(editor.getValue()).isEmpty();
        editor.setValueListener(v -> {
            draft = v;
            keep.active = dev.drimoz.materialnexus.client.ui.Editor.jsonError(v).isEmpty();
        });
        add.accept(keep);
        add.accept(active(Btn.of(Component.translatable("screen.materialnexus.data.discard"), b -> {
            PendingChanges.clearData(selected.kind(), key);
            draft = null;
            rebuild.run();
        }).bounds(bx + bw + 4, y + h - 22, bw, 18).build(), draft != null || PendingChanges.data(selected.kind(), key).isPresent()));
        Button original = Btn.of(Component.translatable("screen.materialnexus.data.original"), b -> {
            PendingChanges.setData(selected.kind(), key, Optional.empty());
            draft = null;
            rebuild.run();
        }).kind(Btn.Kind.DANGER).bounds(bx + 2 * (bw + 4), y + h - 22, bw, 18).build();
        original.active = selectedEdited;
        original.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.materialnexus.data.original.tooltip")));
        add.accept(original);
    }

    private void initUntagged(Consumer<AbstractWidget> add, Font font) {
        List<FormPatterns.Candidate> candidates = list.untagged();
        int pages = Math.max(1, (candidates.size() + page() - 1) / page());
        untaggedPage = Math.clamp(untaggedPage, 0, pages - 1);
        int ry = y + 76;
        for (int i = untaggedPage * page(); i < Math.min(candidates.size(), (untaggedPage + 1) * page()); i++) {
            FormPatterns.Candidate c = candidates.get(i);
            EditBox form = new Field(font, x + w - 258, ry + 2, 120, 16, Component.empty());
            form.setValue(guessForm(c.pattern()));
            add.accept(form);
            boolean done = isDeclared(c.pattern());
            add.accept(active(Btn.of(Component.translatable(done ? "screen.materialnexus.data.declared" : "screen.materialnexus.data.declare"),
                    b -> declare(form.getValue().strip().toLowerCase(Locale.ROOT), c.pattern())).bounds(x + w - 132, ry + 1, 120, 18).build(), !done && declared != null));
            ry += 24;
        }
        add.accept(active(Btn.of(Component.literal("<"), b -> { untaggedPage--; rebuild.run(); }).bounds(x + 8, y + h - 22, 20, 18).build(), untaggedPage > 0));
        add.accept(active(Btn.of(Component.literal(">"), b -> { untaggedPage++; rebuild.run(); }).bounds(x + 32, y + h - 22, 20, 18).build(), untaggedPage < pages - 1));
        // MNX-078: items known as a form (patterns, aliases) get the convention tag they lack, on Apply.
        boolean on = PendingChanges.global("add_missing_tags") instanceof com.google.gson.JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
        Button tags = Btn.of(Component.translatable(on ? "screen.materialnexus.data.missing_tags.on" : "screen.materialnexus.data.missing_tags.off"), b -> {
            PendingChanges.setGlobal("add_missing_tags", new com.google.gson.JsonPrimitive(!on));
            rebuild.run();
        }).bounds(x + w - 232, y + h - 22, 220, 18).build();
        tags.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.materialnexus.data.missing_tags.tooltip")));
        add.accept(tags);

        // MNX-079: one item into one form's tag (c:ingots/tin for tin/ingot), pending like any change.
        int ay = y + h - 44;
        EditBox item = new Field(font, x + 8, ay + 1, 180, 16, Component.empty());
        item.setHint(Component.translatable("screen.materialnexus.data.tag_add.item"));
        item.setValue(tagItem);
        EditBox target = new Field(font, x + 192, ay + 1, 100, 16, Component.empty());
        target.setHint(Component.translatable("screen.materialnexus.data.tag_add.form"));
        target.setValue(tagForm);
        Button addTag = Btn.of(Component.translatable("screen.materialnexus.data.tag_add"), b -> {
            String[] parts = tagForm.strip().toLowerCase(Locale.ROOT).split("/", -1);
            PendingChanges.addToTag(parts[0], parts[1], ResourceLocation.parse(tagItem.strip().toLowerCase(Locale.ROOT)));
            tagItem = "";
            rebuild.run();
        }).bounds(x + 296, ay, 120, 18).build();
        addTag.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.materialnexus.data.tag_add.tooltip")));
        Runnable check = () -> addTag.active = validTagAdd();
        item.setResponder(v -> { tagItem = v; check.run(); });
        target.setResponder(v -> { tagForm = v; check.run(); });
        check.run();
        add.accept(item);
        add.accept(target);
        add.accept(addTag);
    }

    private String tagItem = "";
    private String tagForm = "";

    /** A registered item and a material/form whose form has a convention tag folder; the server checks again. */
    private boolean validTagAdd() {
        ResourceLocation id = ResourceLocation.tryParse(tagItem.strip().toLowerCase(Locale.ROOT));
        String[] parts = tagForm.strip().toLowerCase(Locale.ROOT).split("/", -1);
        return id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id) && parts.length == 2
                && dev.drimoz.materialnexus.core.domain.MaterialId.read(parts[0]).result().isPresent()
                && dev.drimoz.materialnexus.core.domain.FormId.read(parts[1]).result().isPresent();
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
        g.drawString(font, Component.translatable("screen.materialnexus.data_title"), x + 12, y + 6, Theme.TEXT, false);
        int tx = x + 10;
        int ty = y + 20;
        for (boolean mode : new boolean[] {false, true}) {
            Component label = mode ? Component.translatable("screen.materialnexus.data.untagged_count", list == null ? 0 : list.untagged().size())
                    : Component.translatable("screen.materialnexus.data.files");
            int tw = font.width(label) + 12;
            boolean active = untagged == mode;
            boolean over = mx >= tx && mx < tx + tw && my >= ty && my < ty + 16;
            if (active) g.fill(tx, ty + 14, tx + tw, ty + 16, Theme.ACCENT);
            g.drawString(font, label, tx + 6, ty + 4, active ? Theme.TEXT : over ? Theme.MUTED : Theme.MUTED, false);
            hits.add(tx, ty, tw, 16, () -> {
                untagged = mode;
                if (mode && declared == null) PacketDistributor.sendToServer(new DataReadRequest(EditableData.Kind.FORMS.key, DECLARED));
                rebuild.run();
            }, null, List.of());
            tx += tw + 2;
        }
        g.fill(x + 8, ty + 16, x + w - 8, ty + 17, Theme.BORDER);
        Component intro = Component.translatable(untagged ? "screen.materialnexus.data.untagged_intro" : "screen.materialnexus.data.intro");
        g.drawString(font, font.plainSubstrByWidth(intro.getString(), w - 24), x + 12, y + 44, Theme.MUTED, false);
        if (list == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x + 12, y + 64, Theme.MUTED, false);
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
        g.fill(x + side, y + 60, x + side + 1, y + h, Theme.BORDER);
        g.enableScissor(x, top, x + side, bottom);
        int ry = top - (int) scroll;
        for (Row row : rows) {
            if (ry + ROW > top && ry < bottom) {
                if (row.item() == null) {
                    long count = list.entries().stream().filter(e -> e.kind().equals(row.kind().key)).count();
                    g.drawString(font, Component.translatable("screen.materialnexus.data.kind." + row.kind().key).getString() + " (" + count + ")",
                            x + 10, ry + 2, Theme.MUTED, false);
                } else {
                    DataListPayload.Item item = row.item();
                    boolean isSelected = selected != null && selected.kind().equals(item.kind()) && selected.id().equals(item.id());
                    boolean over = my >= top && my < bottom && mx >= x + 4 && mx < x + side - 2 && my >= ry && my < ry + ROW;
                    if (isSelected) g.fill(x + 4, ry, x + side - 2, ry + ROW, Theme.SEL);
                    else if (over) g.fill(x + 4, ry, x + side - 2, ry + ROW, Theme.RAISED);
                    int dot = status(item) == 2 ? Theme.INFO : status(item) == 1 ? Theme.OK : 0;
                    if (dot != 0) g.fill(x + 10, ry + 4, x + 14, ry + 8, dot);
                    // The mod's own files are most of the list: their namespace is implied, others show it.
                    // Read as words; the full id is in the tooltip and above the editor.
                    String words = item.id().getPath().replace("_", " ");
                    String label = item.id().getNamespace().equals(dev.drimoz.materialnexus.MaterialNexus.MOD_ID) ? words
                            : words + " · " + item.id().getNamespace();
                    g.drawString(font, font.plainSubstrByWidth(label, side - 30), x + 18, ry + 2, isSelected ? Theme.TEXT : Theme.MUTED, false);
                    if (my >= top && my < bottom) {
                        hits.add(x + 4, ry, side - 6, ROW, () -> select(item), null,
                                List.of(Component.literal(item.id().toString()), Component.translatable("screen.materialnexus.data.source", item.source()).withColor(Theme.TIP_FAINT)));
                    }
                }
            }
            ry += ROW;
        }
        g.disableScissor();
        if (rows.isEmpty()) g.drawString(font, Component.translatable("screen.materialnexus.data.no_match"), x + 10, top + 2, Theme.FAINT, false);

        int ex = x + side + 10;
        if (selected == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.data.select"), ex, y + 66, Theme.MUTED, false);
            int ky = y + 84;
            for (EditableData.Kind kind : EditableData.Kind.values()) {
                g.fill(ex, ky, ex + 2, ky + 8, Theme.INFO);
                g.drawString(font, Component.translatable("screen.materialnexus.data.kind." + kind.key), ex + 6, ky, Theme.TEXT, false);
                ky += 11;
                for (var line : font.split(Component.translatable("screen.materialnexus.data.kind." + kind.key + ".desc"), x + w - ex - 16)) {
                    g.drawString(font, line, ex + 6, ky, Theme.FAINT, false);
                    ky += 10;
                }
                ky += 6;
            }
            return;
        }
        EditableData.Kind kind = EditableData.Kind.byKey(selected.kind()).orElse(EditableData.Kind.FORMS);
        Component title = Component.translatable("screen.materialnexus.data.kind." + kind.key).copy().append(" · " + selected.id());
        g.drawString(font, font.plainSubstrByWidth(title.getString(), x + w - ex - 120), ex, y + 64, Theme.TEXT, false);
        int s = status(selected);
        Ui.pill(g, font, Component.translatable(s == 2 ? "screen.materialnexus.data.status.pending" : s == 1 ? "screen.materialnexus.data.status.edited"
                : "screen.materialnexus.data.status.original"), x + w - 8 - pillWidth(font, s), y + 62, s == 2 ? Theme.INFO : s == 1 ? Theme.OK : Theme.FAINT);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.data.kind." + kind.key + ".desc").getString(), x + w - ex - 8),
                ex, y + 78, Theme.MUTED, false);
        String text = draft != null ? draft : PendingChanges.data(selected.kind(), selected.id().toString()).map(t -> t.orElse(loadedText)).orElse(loadedText);
        dev.drimoz.materialnexus.client.ui.Editor.jsonError(text).ifPresent(error -> g.drawString(font,
                font.plainSubstrByWidth(Component.translatable("screen.materialnexus.data.invalid", error).getString(), x + w - ex - 8), ex, y + h - 36, Theme.BAD, false));
        // MNX-102: beside the editor when there is room, what this file is for and where it comes from.
        int hx = ex + 612;
        if (x + w - hx >= 160) {
            int hw = x + w - hx - 8;
            g.fill(hx, y + 92, hx + hw, y + h - 28, Theme.SURFACE);
            g.drawString(font, Component.translatable("screen.materialnexus.data.help.title").withStyle(net.minecraft.ChatFormatting.BOLD), hx + 8, y + 100, Theme.TEXT, false);
            int hy = y + 116;
            for (String helpKey : new String[] {"screen.materialnexus.data.kind." + kind.key + ".desc", "screen.materialnexus.data.help.rules"}) {
                for (var line : font.split(Component.translatable(helpKey), hw - 16)) {
                    g.drawString(font, line, hx + 8, hy, Theme.MUTED, false);
                    hy += 10;
                }
                hy += 8;
            }
            for (var line : font.split(Component.translatable("screen.materialnexus.data.source", selected.source()), hw - 16)) {
                g.drawString(font, line, hx + 8, hy, Theme.FAINT, false);
                hy += 10;
            }
        }
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
            g.drawString(font, Component.translatable("screen.materialnexus.data.untagged_none"), x + 12, y + 64, Theme.OK, false);
            return;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.data.col.pattern"), x + 12, y + 62, Theme.FAINT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.data.col.form"), x + w - 258, y + 62, Theme.FAINT, false);
        int ry = y + 76;
        for (int i = untaggedPage * page(); i < Math.min(candidates.size(), (untaggedPage + 1) * page()); i++) {
            FormPatterns.Candidate c = candidates.get(i);
            g.fill(x + 8, ry + 22, x + w - 8, ry + 23, Theme.BORDER);
            g.drawString(font, font.plainSubstrByWidth(c.pattern(), w - 290), x + 12, ry + 2, Theme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.data.materials", c.materials().size(),
                    String.join(", ", c.materials())).getString(), w - 290), x + 12, ry + 12, Theme.FAINT, false);
            ry += 24;
        }
        int pages = Math.max(1, (candidates.size() + page() - 1) / page());
        g.drawString(font, (untaggedPage + 1) + " / " + pages, x + 60, y + h - 17, Theme.MUTED, false);
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
