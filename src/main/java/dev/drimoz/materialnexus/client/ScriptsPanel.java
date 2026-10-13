package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions.Status;
import dev.drimoz.materialnexus.network.ScriptsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scripts view (MNX-076, MNX-099, docs/20): what scripts already decide, per form, against what Material Nexus keeps.
 * One row per decision, those that differ first; the evidence (script lines, recipes touched) is in the inspector of
 * the selected row. "Keep" turns the scripts' item into a pending choice; nothing is written before Review and apply.
 */
final class ScriptsPanel {
    private static final int ROW = 22;
    private static final List<Status> ORDER = List.of(Status.DIFFERS, Status.SAME, Status.UNDECIDED, Status.RECORDED);

    private final boolean readOnly;
    private final Ui.Hits hits = new Ui.Hits();
    private ScriptsPayload payload;
    private ScriptsPayload.Row selected;
    private double scroll;
    private int content, view;

    ScriptsPanel(boolean readOnly) {
        this.readOnly = readOnly;
    }

    void accept(ScriptsPayload payload) {
        this.payload = payload;
        this.selected = null;
    }

    /** Forms scripts keep an item for. */
    int decided() { return payload == null ? 0 : (int) payload.rows().stream().filter(r -> r.kept().isPresent()).count(); }

    /** Of those, the ones Material Nexus keeps another item for. */
    int differing() { return payload == null ? 0 : (int) payload.rows().stream().filter(r -> r.status() == Status.DIFFERS).count(); }

    boolean isEmpty() { return payload == null || payload.rows().isEmpty(); }

    private static boolean offered(ScriptsPayload.Row r) {
        return r.status() == Status.SAME || r.status() == Status.DIFFERS;
    }

    private static boolean pending(ScriptsPayload.Row r) {
        return r.kept().isPresent() && PendingChanges.get(r.material(), r.form()).equals(r.kept());
    }

    private static void keep(ScriptsPayload.Row r) {
        r.kept().ifPresent(item -> PendingChanges.set(r.material(), r.form(), item));
    }

    void scroll(double delta) {
        scroll = Math.clamp(scroll - delta * ROW, 0, Math.max(0, content - view));
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus.scripts." + key, args);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        g.drawString(font, tr("title").copy().withStyle(ChatFormatting.BOLD), x, y, Theme.TEXT, false);
        if (payload == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y + 16, Theme.MUTED, false);
            return;
        }
        g.drawString(font, tr("count", decided(), differing()), x + font.width(tr("title").copy().withStyle(ChatFormatting.BOLD)) + 14, y, Theme.MUTED, false);
        long open = payload.rows().stream().filter(r -> offered(r) && !pending(r)).count();
        if (!readOnly && open > 0) {
            Component all = tr("keep_all.count", open);
            button(g, font, all, x + w - font.width(all) - 12, y - 4, mx, my, () -> payload.rows().stream().filter(ScriptsPanel::offered).forEach(ScriptsPanel::keep));
        }
        int ty = y + 16;
        for (var line : font.split(tr("intro"), Math.min(w, 720))) {
            g.drawString(font, line, x, ty, Theme.MUTED, false);
            ty += 10;
        }
        if (payload.addedRecipes() > 0) {
            g.drawString(font, font.plainSubstrByWidth(tr("added", payload.addedRecipes()).getString(), w), x, ty + 2, Theme.FAINT, false);
            ty += 12;
        }
        ty += 8;

        // Beside the table when there is room, under it otherwise.
        boolean wide = w >= 760;
        int iw = wide ? 320 : w;
        int ih = wide ? y + h - ty : Math.min(120, (y + h - ty) / 2);
        int tw = wide ? w - iw - 12 : w;
        int bottom = wide ? y + h : y + h - ih - 8;
        if (selected == null || !payload.rows().contains(selected)) {
            selected = ORDER.stream().flatMap(s -> payload.rows().stream().filter(r -> r.status() == s)).findFirst().orElse(null);
        }

        int c2 = x + Math.clamp(tw / 4, 110, 170);
        int c3 = c2 + (tw - (c2 - x) - 150) / 2;
        int c4 = x + tw - 150;
        g.drawString(font, tr("col.form"), x + 16, ty + 4, Theme.MUTED, false);
        g.drawString(font, tr("col.scripts"), c2, ty + 4, Theme.MUTED, false);
        g.drawString(font, font.plainSubstrByWidth(tr("col.ours").getString(), c4 - c3 - 4), c3, ty + 4, Theme.MUTED, false);
        int top = ty + Theme.ROW_TEXT;
        view = bottom - top;
        boolean inList = my >= top && my < bottom && mx >= x && mx < x + tw;
        g.enableScissor(x, top, x + tw, bottom);
        int ry = top - (int) scroll;
        for (Status status : ORDER) {
            List<ScriptsPayload.Row> rows = payload.rows().stream().filter(r -> r.status() == status).toList();
            if (rows.isEmpty()) continue;
            g.drawString(font, tr("group." + Names.lowerName(status), rows.size()), x + 4, ry + 8, Theme.MUTED, false);
            ry += 20;
            for (ScriptsPayload.Row r : rows) {
                if (ry + ROW > top && ry < bottom) row(g, font, r, x, ry, tw, c2, c3, c4, mx, my, inList);
                ry += ROW;
            }
        }
        g.disableScissor();
        content = ry + (int) scroll - top;
        scroll = Math.clamp(scroll, 0, Math.max(0, content - view));
        if (content > view && view > 0) {
            int bar = Math.max(12, view * view / content);
            g.fill(x + tw - 4, top, x + tw, bottom, Theme.RAISED);
            int at = (int) (scroll * (view - bar) / (content - view));
            g.fill(x + tw - 4, top + at, x + tw, top + at + bar, Theme.CTRL);
        }
        if (selected != null) inspector(g, font, selected, wide ? x + tw + 12 : x, wide ? ty : bottom + 8, iw, ih, wide, mx, my);
        hits.tooltip(g, font, mx, my);
    }

    private void row(GuiGraphics g, Font font, ScriptsPayload.Row r, int x, int ry, int tw, int c2, int c3, int c4, int mx, int my, boolean inList) {
        boolean hover = inList && my >= ry && my < ry + ROW;
        if (r == selected) g.fill(x, ry, x + tw - 6, ry + ROW, Theme.SEL);
        else if (hover) g.fill(x, ry, x + tw - 6, ry + ROW, Theme.RAISED);
        Ui.mark(g, pending(r) ? Ui.Status.PENDING : r.status() == Status.DIFFERS ? Ui.Status.SUGGESTION : r.status() == Status.UNDECIDED ? Ui.Status.NOTHING : Ui.Status.UNIFIED, x + 5, ry + 8);
        Component title = Component.translatable("screen.materialnexus.triage.item", Names.material(r.material()), Names.form(r.form()));
        g.drawString(font, font.plainSubstrByWidth(title.getString(), c2 - x - 20), x + 16, ry + 7, Theme.TEXT, false);
        r.kept().ifPresent(item -> {
            Ui.slot(g, item, c2, ry + 2, 0, false);
            g.drawString(font, font.plainSubstrByWidth(Names.withMod(item).getString(), c3 - c2 - 28), c2 + 24, ry + 7, Theme.TEXT, false);
        });
        if (r.status() == Status.DIFFERS) {
            if (r.current().isPresent()) {
                Ui.slot(g, r.current().get(), c3, ry + 2, 0, false);
                g.drawString(font, font.plainSubstrByWidth(Names.withMod(r.current().get()).getString(), c4 - c3 - 28), c3 + 24, ry + 7, Theme.MUTED, false);
            } else {
                g.drawString(font, tr("current_none"), c3, ry + 7, Theme.MUTED, false);
            }
        } else if (r.status() == Status.SAME || r.status() == Status.RECORDED) {
            g.drawString(font, tr("same_item"), c3, ry + 7, Theme.FAINT, false);
        }
        if (!inList) return;
        hits.add(x, ry, tw - 6, ROW, () -> selected = r, null, List.of());
        if (readOnly || !offered(r)) return;
        if (pending(r)) {
            g.drawString(font, Ui.Status.PENDING.label(), c4, ry + 7, Theme.INFO, false);
        } else {
            Component keep = tr("keep");
            button(g, font, keep, x + tw - 12 - font.width(keep) - 12, ry + 3, mx, my, () -> keep(r));
        }
    }

    /** Why the scripts are read this way: the lines to delete once applied, and the recipes they touched. */
    private void inspector(GuiGraphics g, Font font, ScriptsPayload.Row r, int ix, int iy, int iw, int ih, boolean wide, int mx, int my) {
        g.fill(ix, iy, ix + iw, iy + ih, Theme.SURFACE);
        int left = ix + 10;
        int inner = iw - 20;
        int cy = iy + 8;
        g.drawString(font, Component.translatable("screen.materialnexus.triage.item", Names.material(r.material()), Names.form(r.form())).withStyle(ChatFormatting.BOLD), left, cy, Theme.TEXT, false);
        Component status = tr("status." + Names.lowerName(r.status()));
        g.drawString(font, status, ix + iw - 10 - font.width(status), cy, r.status() == Status.DIFFERS ? Theme.WARN : Theme.MUTED, false);
        cy += 14;
        g.enableScissor(ix, cy, ix + iw, iy + ih - (wide ? 28 : 4));
        if (r.status() == Status.DIFFERS) {
            Component current = r.current().map(c -> tr("current", Names.withMod(c))).orElse(tr("current_none"));
            for (var line : font.split(current, inner)) {
                g.drawString(font, line, left, cy, Theme.TEXT, false);
                cy += 10;
            }
            cy += 4;
        }
        if (!r.seenIn().isEmpty()) {
            // MNX-077: the script lines to delete once the choice is applied.
            for (var line : font.split(tr("seen_in", String.join(", ", r.seenIn())), inner)) {
                g.drawString(font, line, left, cy, Theme.INFO, false);
                cy += 10;
            }
            cy += 4;
        }
        for (ScriptDecisions.Evidence e : r.evidence()) {
            Component text = tr("evidence." + e.kind(), e.where().toString(), Names.withMod(e.from()), e.to().map(Names::withMod).orElse(Component.empty()));
            for (var line : font.split(text, inner)) {
                g.drawString(font, line, left, cy, Theme.MUTED, false);
                cy += 10;
            }
            cy += 2;
        }
        if (r.evidenceCount() > r.evidence().size()) {
            g.drawString(font, tr("more", r.evidenceCount() - r.evidence().size()), left, cy, Theme.FAINT, false);
        }
        g.disableScissor();
        if (wide && !readOnly && offered(r) && !pending(r)) button(g, font, tr("keep.this"), left, iy + ih - 22, mx, my, () -> keep(r));
    }

    private void button(GuiGraphics g, Font font, Component label, int bx, int by, int mx, int my, Runnable action) {
        int bw = font.width(label) + 12;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        Ui.button(g, font, label, bx, by, bw, 16, false, over, true);
        hits.add(bx, by, bw, 16, action, null, List.of());
    }
}
