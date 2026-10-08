package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.network.PreviewPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview as a side panel of the main screen (MNX-049): every change grouped by kind (choices, tags, recipes,
 * process recipes, items, data, undone), each group folded to its count until opened. Apply / Close at the bottom.
 */
final class PreviewDrawer implements Drawer {
    private static final int LINE = 11;
    /** Effect kinds in reading order; unknown kinds go last under their own name. */
    private static final List<String> ORDER = List.of("script_conflict", "tag_add", "tag_remove", "item_conversion", "conversion_recipe", "recipe_rewrite",
            "recipe_disable", "process_recipe", "process_disable", "item_create", "data_edit", "data_reset",
            "recipe_unsupported", "process_unsupported", "recipe_invalid", "data_invalid", "almost_unified");

    private record Group(String key, int color, List<Component> lines) { }

    private final PreviewPayload preview;
    private final Runnable apply;
    private final Runnable close;
    private final List<Group> groups = new ArrayList<>();
    private final java.util.Set<String> open = new java.util.HashSet<>();
    private final Ui.Hits hits = new Ui.Hits();
    private int x, y, width, height;
    private double scroll;

    PreviewDrawer(PreviewPayload preview, Runnable apply, Runnable close) {
        this.preview = preview;
        this.apply = apply;
        this.close = close;
        List<Component> choices = new ArrayList<>(preview.entries().stream().map(Describe::describe).toList());
        PendingChanges.processes().keySet().stream().sorted()
                .forEach(form -> choices.add(Component.translatable("screen.materialnexus.preview_process", Names.form(form))));
        choices.addAll(PendingChanges.globalLines());
        if (!choices.isEmpty()) groups.add(new Group("choices", Ui.ACCENT, choices));
        Map<String, List<Component>> byKind = new LinkedHashMap<>();
        for (PackContent.Effect e : preview.added()) byKind.computeIfAbsent(e.kind(), k -> new ArrayList<>()).add(Describe.describe(e));
        List<String> kinds = new ArrayList<>(ORDER.stream().filter(byKind::containsKey).toList());
        byKind.keySet().stream().filter(k -> !ORDER.contains(k)).forEach(kinds::add);
        for (String kind : kinds) {
            int color = kind.endsWith("unsupported") || kind.endsWith("invalid") || kind.endsWith("conflict") ? Ui.WARNING : kind.endsWith("disable") ? Ui.DANGER : Ui.SUCCESS;
            groups.add(new Group(kind, color, byKind.get(kind)));
        }
        if (!preview.removed().isEmpty()) {
            groups.add(new Group("removed", Ui.MUTED | 0xFF000000, preview.removed().stream().map(Describe::describe).toList()));
        }
        if (groups.size() == 1) open.add(groups.get(0).key());
    }

    boolean hasChanges() {
        // A script conflict is a warning (MNX-076): alone, it is nothing to apply.
        return preview.entries().stream().anyMatch(PolicyEditor.Entry::valid)
                || preview.added().stream().anyMatch(e -> !e.kind().equals(PackContent.SCRIPT_CONFLICT)) || !preview.removed().isEmpty()
                || PendingChanges.size() > 0;
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    @Override
    public boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }

    @Override
    public void scroll(double delta) {
        scroll = Math.max(0, scroll - delta * LINE * 3);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button) || contains(mx, my);
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        g.fill(x, y, x + width, y + height, 0xFF141418);
        g.fill(x, y, x + 1, y + height, Ui.ACCENT);
        g.drawString(font, Component.translatable("screen.materialnexus.preview_title"), x + 8, y + 6, Ui.TEXT, false);
        preview.preset().ifPresent(id -> g.drawString(font, Component.translatable("screen.materialnexus.preview_preset",
                Component.translatableWithFallback("materialnexus.preset." + id.getPath(), id.getPath())), x + 8, y + 17, 0x88AAFF, false));

        int listTop = y + 30;
        int listBottom = y + height - 28;
        g.enableScissor(x, listTop, x + width, listBottom);
        int ly = listTop - (int) scroll;
        if (!hasChanges()) g.drawString(font, Component.translatable("screen.materialnexus.no_changes"), x + 8, ly, Ui.MUTED, false);
        for (Group group : groups) {
            boolean expanded = open.contains(group.key());
            boolean over = mx >= x && mx < x + width && my >= ly && my < ly + 14 && my >= listTop && my < listBottom;
            if (over) g.fill(x + 2, ly, x + width - 2, ly + 14, 0x18FFFFFF);
            g.fill(x + 6, ly + 3, x + 9, ly + 11, group.color());
            Component title = Component.translatable("screen.materialnexus.group." + group.key(), group.lines().size());
            g.drawString(font, (expanded ? "▾ " : "▸ ") + title.getString(), x + 13, ly + 3, Ui.TEXT, false);
            String key = group.key();
            if (ly + 14 > listTop && ly < listBottom) {
                hits.add(x, Math.max(ly, listTop), width, 14, () -> { if (!open.remove(key)) open.add(key); }, null, List.of());
            }
            ly += 15;
            if (!expanded) continue;
            for (Component line : group.lines()) {
                if (ly + LINE > listTop && ly < listBottom) {
                    g.drawString(font, font.plainSubstrByWidth(line.getString(), width - 24), x + 16, ly, Ui.MUTED, false);
                }
                ly += LINE;
            }
            ly += 3;
        }
        int contentHeight = ly + (int) scroll - listTop;
        scroll = Math.min(scroll, Math.max(0, contentHeight - (listBottom - listTop)));
        g.disableScissor();

        int by = y + height - 22;
        button(g, font, Component.translatable("screen.materialnexus.apply"), x + 8, by, hasChanges() ? Ui.ACCENT : Ui.NEUTRAL, mx, my,
                hasChanges() ? apply : null);
        button(g, font, Component.translatable("screen.materialnexus.close"), x + 8 + 90, by, Ui.NEUTRAL, mx, my, close);
    }

    private void button(GuiGraphics g, Font font, Component label, int bx, int by, int color, int mx, int my, Runnable action) {
        int bw = 84;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        g.fill(bx, by, bx + bw, by + 16, over && action != null ? (color & 0x00FFFFFF) | 0xC0000000 : (color & 0x00FFFFFF) | 0x80000000);
        g.drawCenteredString(font, label, bx + bw / 2, by + 4, Ui.TEXT);
        if (action != null) hits.add(bx, by, bw, 16, action, null, List.of());
    }
}
