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
import dev.drimoz.materialnexus.network.PreviewRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * In-game editor of Material Nexus data (MNX-046). "Files": every forms / template / format / preset / material file,
 * edited as JSON; an edit replaces the original, "Original" removes the edit. "Untagged forms": the audit of item name
 * shapes, each declarable as a form pattern in one click. Every edit is a pending change: Preview, then Apply.
 */
public final class DataScreen extends Screen {
    /** Patterns declared from the audit go to this file of the edits pack. */
    static final ResourceLocation DECLARED = ResourceLocation.fromNamespaceAndPath("materialnexus_user", "declared_patterns");
    private static final int PAGE = 12;

    private final Screen parent;
    private DataListPayload list;
    private boolean untaggedMode;
    private String filter = "";
    private DataListPayload.Item selected;
    private String loadedText = "";
    private boolean selectedEdited;
    private MultiLineEditBox editor;
    private Files files;
    private int untaggedPage;
    private String newKind = EditableData.Kind.FORMS.key;
    private String newId = "materialnexus_user:custom";
    private JsonObject declared;

    DataScreen(Screen parent) {
        super(Component.translatable("screen.materialnexus.data_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (list == null) PacketDistributor.sendToServer(DataListRequest.INSTANCE);
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.files"), b -> { untaggedMode = false; rebuildWidgets(); })
                .bounds(6, 3, 90, 18).build()).active = untaggedMode;
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.untagged"), b -> {
            untaggedMode = true;
            if (declared == null) PacketDistributor.sendToServer(new DataReadRequest(EditableData.Kind.FORMS.key, DECLARED));
            rebuildWidgets();
        }).bounds(100, 3, 120, 18).build()).active = !untaggedMode;
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.preview_button", PendingChanges.size()),
                b -> PacketDistributor.sendToServer(new PreviewRequest(PendingChanges.all(), false, Optional.empty(),
                        PendingChanges.processes(), PendingChanges.creations(), PendingChanges.data())))
                .bounds(width - 212, 3, 102, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(width - 106, 3, 100, 18).build());
        if (list == null) return;
        if (untaggedMode) initUntagged();
        else initFiles();
    }

    // ---- files ---------------------------------------------------------------------------------------------

    private void initFiles() {
        int side = Math.max(160, width / 3);
        EditBox search = new EditBox(font, 6, 26, side - 12, 16, Component.translatable("screen.materialnexus.search"));
        search.setHint(Component.translatable("screen.materialnexus.search"));
        search.setValue(filter);
        search.setResponder(v -> { filter = v; if (files != null) files.show(); });
        addRenderableWidget(search);
        files = addRenderableWidget(new Files(minecraft, side, height - 46 - 50, 46));
        files.show();

        // New file: kind (cycles) + id.
        addRenderableWidget(Button.builder(Component.literal(newKind), b -> {
            var kinds = EditableData.Kind.values();
            newKind = kinds[(EditableData.Kind.byKey(newKind).orElseThrow().ordinal() + 1) % kinds.length].key;
            rebuildWidgets();
        }).bounds(6, height - 46, 100, 18).build());
        EditBox id = new EditBox(font, 110, height - 45, side - 116, 16, Component.empty());
        id.setValue(newId);
        id.setResponder(v -> newId = v);
        addRenderableWidget(id);
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.new"), b -> {
            ResourceLocation rl = ResourceLocation.tryParse(newId.strip().toLowerCase(Locale.ROOT));
            if (rl != null) select(new DataListPayload.Item(newKind, rl, EditableData.USER_PACK_ID, false));
        }).bounds(6, height - 24, side - 12, 18).build());

        int x = side + 8;
        editor = new MultiLineEditBox(font, x, 38, width - x - 6, height - 38 - 30, Component.translatable("screen.materialnexus.data.select"), Component.empty());
        editor.setCharacterLimit(EditableData.MAX_TEXT);
        if (selected != null) {
            editor.setValue(PendingChanges.data(selected.kind(), selected.id().toString()).map(t -> t.orElse(loadedText)).orElse(loadedText));
        }
        addRenderableWidget(editor);
        if (selected == null) return;
        int by = height - 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.keep"), b -> {
            if (editor.getValue().equals(loadedText)) PendingChanges.clearData(selected.kind(), selected.id().toString());
            else PendingChanges.setData(selected.kind(), selected.id().toString(), Optional.of(editor.getValue()));
            rebuildWidgets();
        }).bounds(x, by, 130, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.discard"), b -> {
            PendingChanges.clearData(selected.kind(), selected.id().toString());
            rebuildWidgets();
        }).bounds(x + 134, by, 130, 18).build()).active = PendingChanges.data(selected.kind(), selected.id().toString()).isPresent();
        addRenderableWidget(Button.builder(Component.translatable("screen.materialnexus.data.original"), b -> {
            PendingChanges.setData(selected.kind(), selected.id().toString(), Optional.empty());
            rebuildWidgets();
        }).bounds(x + 268, by, 130, 18).build()).active = selectedEdited;
    }

    private void select(DataListPayload.Item item) {
        selected = item;
        loadedText = "";
        selectedEdited = item.edited();
        PacketDistributor.sendToServer(new DataReadRequest(item.kind(), item.id()));
        rebuildWidgets();
    }

    void acceptList(DataListPayload payload) {
        list = payload;
        rebuildWidgets();
    }

    void acceptRead(DataReadPayload payload) {
        if (payload.kind().equals(EditableData.Kind.FORMS.key) && payload.id().equals(DECLARED) && declared == null) {
            declared = PendingChanges.data(payload.kind(), DECLARED.toString()).flatMap(t -> t)
                    .or(payload::text)
                    .map(t -> JsonParser.parseString(t).getAsJsonObject())
                    .orElseGet(JsonObject::new);
            rebuildWidgets();
        }
        if (selected != null && payload.kind().equals(selected.kind()) && payload.id().equals(selected.id())) {
            loadedText = payload.text().orElse("{\n}");
            selectedEdited = payload.edited();
            rebuildWidgets();
        }
    }

    private final class Files extends ObjectSelectionList<Files.Row> {
        Files(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 20);
        }

        void show() {
            String f = filter.strip().toLowerCase(Locale.ROOT);
            replaceEntries(list.entries().stream()
                    .filter(e -> (e.kind() + " " + e.id()).contains(f))
                    .map(Row::new).toList());
        }

        @Override public int getRowWidth() { return width - 12; }

        @Override protected int getScrollbarPosition() { return getX() + width - 6; }

        final class Row extends ObjectSelectionList.Entry<Row> {
            private final DataListPayload.Item item;

            Row(DataListPayload.Item item) { this.item = item; }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hovering, float pt) {
                boolean pending = PendingChanges.data(item.kind(), item.id().toString()).isPresent();
                int color = pending ? 0xFFCC33 : item.edited() ? 0x55FF55 : 0xFFFFFF;
                boolean isSelected = selected != null && selected.kind().equals(item.kind()) && selected.id().equals(item.id());
                if (isSelected) g.fill(left - 2, top - 1, left + width, top + height - 1, 0x44FFFFFF);
                g.drawString(font, font.plainSubstrByWidth(item.id().toString(), width - 4), left + 2, top + 1, color);
                g.drawString(font, font.plainSubstrByWidth(item.kind() + " · " + item.source(), width - 4), left + 2, top + 10, 0x888888);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                select(item);
                return true;
            }

            @Override public Component getNarration() { return Component.literal(item.id().toString()); }
        }
    }

    // ---- untagged forms ------------------------------------------------------------------------------------

    private void initUntagged() {
        List<FormPatterns.Candidate> candidates = list.untagged();
        int pages = Math.max(1, (candidates.size() + PAGE - 1) / PAGE);
        untaggedPage = Math.clamp(untaggedPage, 0, pages - 1);
        int y = 44;
        for (int i = untaggedPage * PAGE; i < Math.min(candidates.size(), (untaggedPage + 1) * PAGE); i++) {
            FormPatterns.Candidate c = candidates.get(i);
            EditBox form = new EditBox(font, width - 250, y, 120, 16, Component.empty());
            form.setValue(guessForm(c.pattern()));
            addRenderableWidget(form);
            boolean done = isDeclared(c.pattern());
            addRenderableWidget(Button.builder(Component.translatable(done ? "screen.materialnexus.data.declared" : "screen.materialnexus.data.declare"),
                    b -> declare(form.getValue().strip().toLowerCase(Locale.ROOT), c.pattern())).bounds(width - 126, y - 1, 120, 18).build()).active = !done && declared != null;
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { untaggedPage--; rebuildWidgets(); }).bounds(6, height - 24, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { untaggedPage++; rebuildWidgets(); }).bounds(30, height - 24, 20, 18).build());
    }

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
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (list == null) {
            g.drawCenteredString(font, Component.translatable("screen.materialnexus.process.loading"), width / 2, 40, 0x888888);
            return;
        }
        if (untaggedMode) {
            g.drawString(font, Component.translatable("screen.materialnexus.data.untagged_intro"), 6, 28, 0xAAAAAA);
            int y = 44;
            for (int i = untaggedPage * PAGE; i < Math.min(list.untagged().size(), (untaggedPage + 1) * PAGE); i++) {
                FormPatterns.Candidate c = list.untagged().get(i);
                g.drawString(font, font.plainSubstrByWidth(c.pattern(), width - 270), 6, y + 1, 0xFFFFFF);
                g.drawString(font, font.plainSubstrByWidth(c.materials().size() + ": " + String.join(", ", c.materials()), width - 270), 6, y + 10, 0x888888);
                y += 22;
            }
        } else if (selected != null) {
            int side = Math.max(160, width / 3);
            g.drawString(font, font.plainSubstrByWidth(selected.kind() + " · " + selected.id(), width - side - 20), side + 8, 27, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
