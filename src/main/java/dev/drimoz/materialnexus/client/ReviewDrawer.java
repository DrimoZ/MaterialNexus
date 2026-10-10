package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.network.PreviewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Review and apply, as one side panel in two steps (MNX-097, docs/22 section 6.8). "Your changes": what was asked, each
 * line and each group removable, nothing sent to the server. "Effects on the pack": what applying would do, asked from
 * the server when the tab opens, grouped by gravity: what to look at first, then the changes, then what is undone.
 */
final class ReviewDrawer implements Drawer {
    private static final int LINE = 12;
    private static final int HEAD = 44;
    private static final int FOOT = 28;
    /** Effect kinds in reading order; unknown kinds go last under their own name. */
    private static final List<String> ORDER = List.of("script_conflict", "recipe_disable", "process_disable", "recipe_unsupported", "process_unsupported",
            "recipe_invalid", "data_invalid", "almost_unified", "tag_add", "tag_remove", "item_conversion", "conversion_recipe", "recipe_rewrite",
            "process_recipe", "item_create", "data_edit", "data_reset");

    private record Change(Component text, int depth, boolean group, Runnable discard) { }

    /** {@code level}: 2 to look at (something is switched off), 1 to look at (not handled), 0 a plain change. */
    private record Group(String key, int level, List<Component> lines) { }

    private final Runnable requestEffects;
    private final Consumer<Optional<ResourceLocation>> apply;
    private final Runnable close;
    private final Ui.Hits hits = new Ui.Hits();
    private final Set<String> open = new HashSet<>();
    private boolean effects;
    private PreviewPayload preview;
    private List<Group> groups = List.of();
    private long asked;
    private int x, y, width, height;
    private double scroll;

    ReviewDrawer(boolean effects, Runnable requestEffects, Consumer<Optional<ResourceLocation>> apply, Runnable close) {
        this.requestEffects = requestEffects;
        this.apply = apply;
        this.close = close;
        if (effects) showEffects();
    }

    boolean onEffects() { return effects; }

    /** The effects are those of the changes as they are now: asked again each time the tab opens. */
    private void showEffects() {
        effects = true;
        preview = null;
        scroll = 0;
        asked = Util.getMillis();
        requestEffects.run();
    }

    void accept(PreviewPayload payload) {
        preview = payload;
        Map<String, List<Component>> byKind = new LinkedHashMap<>();
        for (PackContent.Effect e : payload.added()) byKind.computeIfAbsent(e.kind(), k -> new ArrayList<>()).add(Describe.describe(e));
        List<String> kinds = new ArrayList<>(ORDER.stream().filter(byKind::containsKey).toList());
        byKind.keySet().stream().filter(k -> !ORDER.contains(k)).forEach(kinds::add);
        List<Group> out = new ArrayList<>();
        for (String kind : kinds) {
            int level = kind.endsWith("disable") ? 2 : kind.endsWith("unsupported") || kind.endsWith("invalid") || kind.endsWith("conflict") || kind.equals("almost_unified") ? 1 : 0;
            out.add(new Group(kind, level, byKind.get(kind)));
        }
        if (!payload.removed().isEmpty()) out.add(new Group("removed", -1, payload.removed().stream().map(Describe::describe).toList()));
        groups = out;
    }

    private boolean hasChanges() {
        // A script conflict is a warning (MNX-076): alone, it is nothing to apply.
        return preview != null && (preview.entries().stream().anyMatch(PolicyEditor.Entry::valid)
                || preview.added().stream().anyMatch(e -> !e.kind().equals(PackContent.SCRIPT_CONFLICT)) || !preview.removed().isEmpty()
                || PendingChanges.size() > 0);
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

    // ---- your changes --------------------------------------------------------------------------------------

    private static Component group(String key, int count) {
        return Component.translatable("screen.materialnexus.pending.group." + key, count);
    }

    private static List<Change> changes() {
        List<Change> out = new ArrayList<>();
        Map<String, List<CanonicalChange>> byMaterial = new TreeMap<>();
        PendingChanges.all().forEach(c -> byMaterial.computeIfAbsent(c.material(), m -> new ArrayList<>()).add(c));
        if (!byMaterial.isEmpty()) {
            out.add(new Change(group("choices", PendingChanges.all().size()), 0, true,
                    () -> PendingChanges.all().forEach(c -> PendingChanges.clear(c.material(), c.form()))));
            byMaterial.forEach((material, list) -> {
                out.add(new Change(Component.literal(Names.material(material).getString() + " (" + list.size() + ")"), 1, true,
                        () -> list.forEach(c -> PendingChanges.clear(c.material(), c.form()))));
                for (CanonicalChange c : list) {
                    out.add(new Change(Component.literal(Names.form(c.form()).getString() + " → ").append(c.reset()
                            ? Component.translatable("screen.materialnexus.reset.label") : Names.withMod(c.provider())),
                            2, false, () -> PendingChanges.clear(c.material(), c.form())));
                }
            });
        }
        var processes = new TreeMap<>(PendingChanges.processes()).keySet();
        if (!processes.isEmpty()) {
            out.add(new Change(group("rules", processes.size()), 0, true, () -> processes.forEach(PendingChanges::clearProcess)));
            processes.forEach(form -> out.add(new Change(Component.translatable("screen.materialnexus.preview_process", Names.form(form)), 1, false,
                    () -> PendingChanges.clearProcess(form))));
        }
        List<String> creations = PendingChanges.creations();
        if (!creations.isEmpty()) {
            out.add(new Change(group("items", creations.size()), 0, true, () -> creations.forEach(PendingChanges::toggleCreation)));
            creations.forEach(c -> {
                String[] parts = c.split("/", 2);
                out.add(new Change(Component.translatable("screen.materialnexus.triage.item", Names.material(parts[0]), Names.form(parts[1])), 1, false,
                        () -> PendingChanges.toggleCreation(c)));
            });
        }
        var data = new TreeMap<>(PendingChanges.data()).keySet();
        if (!data.isEmpty()) {
            out.add(new Change(group("data", data.size()), 0, true, () -> data.forEach(PendingChanges::clearData)));
            data.forEach(key -> out.add(new Change(Component.literal(key.replace("|", " · ")), 1, false, () -> PendingChanges.clearData(key))));
        }
        List<Map.Entry<Component, Runnable>> settings = PendingChanges.globalEdits();
        if (!settings.isEmpty()) {
            out.add(new Change(group("settings", settings.size()), 0, true, () -> settings.forEach(e -> e.getValue().run())));
            settings.forEach(e -> out.add(new Change(e.getKey(), 1, false, e.getValue())));
        }
        return out;
    }

    // ---- render --------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        g.fill(x, y, x + width, y + height, Theme.SURFACE);
        g.fill(x, y, x + 1, y + height, Theme.BORDER);
        g.drawString(font, Component.translatable("screen.materialnexus.preview_short").withStyle(ChatFormatting.BOLD), x + 12, y + 8, Theme.TEXT, false);

        // The two steps, as tabs.
        int tx = x + 12;
        Component changesTab = Component.translatable("screen.materialnexus.review.tab.changes", PendingChanges.size());
        Component effectsTab = preview == null ? Component.translatable("screen.materialnexus.review.tab.effects")
                : Component.translatable("screen.materialnexus.review.tab.effects.count", preview.added().size() + preview.removed().size());
        tx = tab(g, font, changesTab, tx, !effects, mx, my, () -> { effects = false; scroll = 0; });
        tab(g, font, effectsTab, tx, effects, mx, my, this::showEffects);
        g.fill(x + 1, y + 40, x + width, y + 41, Theme.BORDER);

        int top = y + HEAD;
        int bottom = y + height - FOOT;
        boolean inList = my >= top && my < bottom && mx >= x && mx < x + width;
        g.enableScissor(x + 1, top, x + width, bottom);
        int end = effects ? renderEffects(g, font, top, bottom, mx, my, inList) : renderChanges(g, font, top, bottom, mx, my, inList);
        g.disableScissor();
        scroll = Math.min(scroll, Math.max(0, end + (int) scroll - bottom));

        g.fill(x + 1, bottom, x + width, bottom + 1, Theme.BORDER);
        int by = bottom + 6;
        Component closeLabel = Component.translatable("screen.materialnexus.close");
        int right = x + width - 12;
        if (effects) {
            // The one primary action while this panel is open.
            Component applyLabel = Component.translatable("screen.materialnexus.apply");
            int aw = font.width(applyLabel) + 24;
            button(g, font, applyLabel, right - aw, by, aw, true, hasChanges(), mx, my, () -> apply.accept(preview.preset()));
            int cw = font.width(closeLabel) + 16;
            button(g, font, closeLabel, right - aw - 4 - cw, by, cw, false, true, mx, my, close);
            if (preview != null) {
                long disabled = preview.added().stream().filter(e -> e.kind().endsWith("disable")).count();
                Component summary = Component.translatable("screen.materialnexus.review.summary", preview.added().size(), disabled);
                g.drawString(font, font.plainSubstrByWidth(summary.getString(), right - aw - cw - 20 - x - 12), x + 12, by + 5, Theme.MUTED, false);
            }
        } else {
            Component next = Component.translatable("screen.materialnexus.review.see_effects");
            int nw = font.width(next) + 16;
            button(g, font, next, right - nw, by, nw, false, PendingChanges.size() > 0, mx, my, this::showEffects);
            int cw = font.width(closeLabel) + 16;
            button(g, font, closeLabel, right - nw - 4 - cw, by, cw, false, true, mx, my, close);
        }
        hits.tooltip(g, font, mx, my);
    }

    private int tab(GuiGraphics g, Font font, Component label, int tx, boolean active, int mx, int my, Runnable action) {
        int w = font.width(label) + 12;
        boolean over = mx >= tx && mx < tx + w && my >= y + 22 && my < y + 40;
        if (active) g.fill(tx, y + 38, tx + w, y + 40, Theme.ACCENT);
        g.drawString(font, label, tx + 6, y + 27, active || over ? Theme.TEXT : Theme.MUTED, false);
        if (!active) hits.add(tx, y + 22, w, 18, action, null, List.of());
        return tx + w + 4;
    }

    private int renderChanges(GuiGraphics g, Font font, int top, int bottom, int mx, int my, boolean inList) {
        int ly = top + 4 - (int) scroll;
        g.drawString(font, Component.translatable("screen.materialnexus.review.changes.hint"), x + 12, ly, Theme.MUTED, false);
        ly += 16;
        List<Change> lines = changes();
        if (lines.isEmpty()) g.drawString(font, Component.translatable("screen.materialnexus.pending.none"), x + 12, ly, Theme.FAINT, false);
        Component remove = Component.translatable("screen.materialnexus.review.remove");
        Component removeAll = Component.translatable("screen.materialnexus.review.remove_all");
        for (Change line : lines) {
            if (ly + 18 > top && ly < bottom) {
                int lx = x + 12 + line.depth() * 12;
                boolean over = inList && my >= ly && my < ly + 18;
                if (over) g.fill(x + 1, ly, x + width, ly + 18, Theme.RAISED);
                Component label = line.group() ? removeAll : remove;
                int bw = font.width(label) + 12;
                int bx = x + width - 12 - bw;
                if (!line.group()) g.renderOutline(lx, ly + 6, 6, 6, Theme.INFO);
                g.drawString(font, font.plainSubstrByWidth(line.text().getString(), bx - lx - (line.group() ? 4 : 16)), line.group() ? lx : lx + 12, ly + 5,
                        line.group() ? Theme.TEXT : Theme.MUTED, false);
                // The way back is shown on the row under the pointer, and on every group.
                if (over || line.depth() == 0) {
                    boolean overButton = over && mx >= bx && mx < bx + bw;
                    if (overButton) g.fill(bx, ly + 1, bx + bw, ly + 17, Theme.SEL);
                    g.drawString(font, label, bx + 6, ly + 5, overButton ? Theme.TEXT : Theme.MUTED, false);
                }
                if (inList) hits.add(bx, ly + 1, bw, 16, line.discard(), null, List.of());
            }
            ly += 18;
        }
        return ly;
    }

    private int renderEffects(GuiGraphics g, Font font, int top, int bottom, int mx, int my, boolean inList) {
        int ly = top + 4 - (int) scroll;
        if (preview == null) {
            // The server is working it out: several seconds on a large pack.
            g.drawString(font, Component.translatable("screen.materialnexus.review.computing"), x + 12, ly, Theme.MUTED, false);
            int w = width - 24;
            int at = (int) ((Util.getMillis() - asked) / 6 % (w + 60)) - 60;
            g.fill(x + 12, ly + 14, x + 12 + w, ly + 16, Theme.RAISED);
            g.fill(Math.max(x + 12, x + 12 + at), ly + 14, Math.min(x + 12 + w, x + 12 + at + 60), ly + 16, Theme.ACCENT);
            return ly + 20;
        }
        if (preview.preset().isPresent()) {
            g.drawString(font, Component.translatable("screen.materialnexus.preview_preset",
                    Component.translatableWithFallback("materialnexus.preset." + preview.preset().get().getPath(), preview.preset().get().getPath())), x + 12, ly, Theme.INFO, false);
            ly += 14;
        }
        if (!hasChanges()) {
            g.drawString(font, Component.translatable("screen.materialnexus.no_changes"), x + 12, ly, Theme.MUTED, false);
            return ly + 12;
        }
        ly = section(g, font, "look", groups.stream().filter(gr -> gr.level() > 0).toList(), ly, top, bottom, my, inList);
        ly = section(g, font, "changes", groups.stream().filter(gr -> gr.level() == 0).toList(), ly, top, bottom, my, inList);
        return section(g, font, "undone", groups.stream().filter(gr -> gr.level() < 0).toList(), ly, top, bottom, my, inList);
    }

    private int section(GuiGraphics g, Font font, String key, List<Group> list, int ly, int top, int bottom, int my, boolean inList) {
        if (list.isEmpty()) return ly;
        g.drawString(font, Component.translatable("screen.materialnexus.review.section." + key).withStyle(ChatFormatting.BOLD), x + 12, ly + 4, Theme.TEXT, false);
        ly += 18;
        for (Group group : list) {
            boolean expanded = open.contains(group.key());
            if (ly + 18 > top && ly < bottom) {
                boolean over = inList && my >= ly && my < ly + 18;
                if (over) g.fill(x + 1, ly, x + width, ly + 18, Theme.RAISED);
                Ui.caret(g, x + 12, ly + 6, expanded, Theme.MUTED);
                mark(g, group.level(), x + 22, ly + 6);
                Component title = Component.translatable("screen.materialnexus.group." + group.key(), group.lines().size());
                g.drawString(font, font.plainSubstrByWidth(title.getString(), width - 48), x + 34, ly + 5, Theme.TEXT, false);
                String groupKey = group.key();
                if (inList) hits.add(x, ly, width, 18, () -> { if (!open.remove(groupKey)) open.add(groupKey); }, null, List.of());
            }
            ly += 18;
            if (!expanded) continue;
            for (Component line : group.lines()) {
                if (ly + LINE > top && ly < bottom) g.drawString(font, font.plainSubstrByWidth(line.getString(), width - 48), x + 34, ly + 1, Theme.MUTED, false);
                ly += LINE;
            }
            ly += 4;
        }
        return ly + 6;
    }

    /** Shape and colour together: a cross for what is switched off, a dot for what needs a look, a tick for a change. */
    private static void mark(GuiGraphics g, int level, int x, int y) {
        if (level == 2) {
            for (int i = 0; i < 6; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, Theme.BAD);
                g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, Theme.BAD);
            }
        } else if (level == 1) Ui.mark(g, Ui.Status.SUGGESTION, x, y);
        else if (level == 0) Ui.mark(g, Ui.Status.UNIFIED, x, y);
    }

    private void button(GuiGraphics g, Font font, Component label, int bx, int by, int bw, boolean primary, boolean active, int mx, int my, Runnable action) {
        boolean over = active && mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        Ui.button(g, font, label, bx, by, bw, 16, primary, over, active);
        if (active) hits.add(bx, by, bw, 16, action, null, List.of());
    }
}
