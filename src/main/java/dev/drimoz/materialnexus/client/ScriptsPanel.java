package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.scripts.ScriptDecisions;
import dev.drimoz.materialnexus.core.scripts.ScriptDecisions.Status;
import dev.drimoz.materialnexus.network.ScriptsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scripts view (MNX-076, docs/20): what scripts already decide, per form, against what Material Nexus keeps. "Keep"
 * turns the scripts' item into a pending choice, like any other; nothing is written before Preview and apply.
 */
final class ScriptsPanel {
    private static final int LINE = 10;

    private final boolean readOnly;
    private final Ui.Hits hits = new Ui.Hits();
    private ScriptsPayload payload;
    private double scroll;

    ScriptsPanel(boolean readOnly) {
        this.readOnly = readOnly;
    }

    void accept(ScriptsPayload payload) {
        this.payload = payload;
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

    private void keepAll() {
        payload.rows().stream().filter(ScriptsPanel::offered).forEach(ScriptsPanel::keep);
    }

    void scroll(double delta) {
        scroll = Math.max(0, scroll - delta * LINE * 3);
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        g.drawString(font, Component.translatable("screen.materialnexus.scripts.title"), x, y, Ui.TEXT, false);
        int ty = y + 14;
        for (var line : font.split(Component.translatable("screen.materialnexus.scripts.intro"), Math.min(w, 600))) {
            g.drawString(font, line, x, ty, Ui.MUTED, false);
            ty += 11;
        }
        if (payload == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, ty + 6, Ui.MUTED, false);
            return;
        }
        ty += 4;
        if (!readOnly && payload.rows().stream().anyMatch(r -> offered(r) && !pending(r))) {
            ty = button(g, font, Component.translatable("screen.materialnexus.scripts.keep_all"), x, ty, mx, my, this::keepAll) + 4;
        }
        if (payload.addedRecipes() > 0) {
            g.drawString(font, Component.translatable("screen.materialnexus.scripts.added", payload.addedRecipes()), x, ty, Ui.FAINT, false);
            ty += 12;
        }

        int top = ty + 2;
        int bottom = y + h;
        g.enableScissor(x, top, x + w, bottom);
        int ry = top - (int) scroll;
        for (ScriptsPayload.Row r : payload.rows()) {
            ry = row(g, font, r, x, ry, w, top, bottom, mx, my) + 6;
        }
        g.disableScissor();
        scroll = Math.min(scroll, Math.max(0, ry + (int) scroll - bottom));
        hits.tooltip(g, font, mx, my);
    }

    /** One decision: the kept item, its form, status, what Material Nexus keeps, and why. Returns the y below it. */
    private int row(GuiGraphics g, Font font, ScriptsPayload.Row r, int x, int y, int w, int top, int bottom, int mx, int my) {
        boolean visible = y + 22 > top && y < bottom;
        g.fill(x, y, x + w, y + 22, 0x0CFFFFFF);
        r.kept().ifPresent(item -> Ui.slot(g, item, x + 2, y + 2, 0, false));
        Component title = Component.translatable("screen.materialnexus.triage.item", Names.material(r.material()), Names.form(r.form()));
        g.drawString(font, title, x + 26, y + 7, Ui.TEXT, false);
        int px = x + 32 + font.width(title);
        px += 6 + Ui.pill(g, font, Component.translatable("screen.materialnexus.scripts.status." + Names.lowerName(r.status())), px, y + 5, color(r.status()));
        if (r.status() == Status.DIFFERS) {
            Component current = r.current().map(c -> Component.translatable("screen.materialnexus.scripts.current", Names.withMod(c)))
                    .orElse(Component.translatable("screen.materialnexus.scripts.current_none"));
            g.drawString(font, current, px, y + 7, Ui.MUTED, false);
        }
        if (!readOnly && offered(r)) {
            if (pending(r)) {
                Ui.pill(g, font, Ui.Status.PENDING.label(), x + w - 60, y + 5, Ui.ACCENT);
            } else if (visible) {
                button(g, font, Component.translatable("screen.materialnexus.scripts.keep"), x + w - 60, y + 3, mx, my, () -> keep(r));
            }
        }
        if (visible) {
            r.kept().ifPresent(item -> hits.add(x + 2, y + 2, 18, 18, null, null,
                    List.of(Names.stack(item).getHoverName(), Component.literal(item.toString()).withColor(0x888888))));
        }
        int ly = y + 25;
        if (!r.seenIn().isEmpty()) {
            // MNX-077: the script lines to delete once the choice is applied.
            Component seen = Component.translatable("screen.materialnexus.scripts.seen_in", String.join(", ", r.seenIn()));
            g.drawString(font, font.plainSubstrByWidth(seen.getString(), w - 30), x + 26, ly, 0x88AAFF, false);
            ly += LINE + 1;
        }
        for (ScriptDecisions.Evidence e : r.evidence()) {
            g.drawString(font, font.plainSubstrByWidth(evidence(e).getString(), w - 30), x + 26, ly, Ui.MUTED, false);
            ly += LINE;
        }
        if (r.evidenceCount() > r.evidence().size()) {
            g.drawString(font, Component.translatable("screen.materialnexus.scripts.more", r.evidenceCount() - r.evidence().size()), x + 26, ly, Ui.FAINT, false);
            ly += LINE;
        }
        return ly;
    }

    private static Component evidence(ScriptDecisions.Evidence e) {
        Component from = Names.withMod(e.from());
        Component to = e.to().map(Names::withMod).orElse(Component.empty());
        return Component.translatable("screen.materialnexus.scripts.evidence." + e.kind(), e.where().toString(), from, to);
    }

    private static int color(Status status) {
        return switch (status) {
            case RECORDED -> Ui.SUCCESS;
            case SAME -> Ui.ACCENT;
            case DIFFERS -> Ui.WARNING;
            case UNDECIDED -> Ui.NEUTRAL;
        };
    }

    private int button(GuiGraphics g, Font font, Component label, int bx, int by, int mx, int my, Runnable action) {
        int bw = Math.max(56, font.width(label) + 16);
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        g.fill(bx, by, bx + bw, by + 16, over ? 0xFF6AABF6 : Ui.ACCENT);
        g.drawCenteredString(font, label, bx + bw / 2, by + 4, Ui.TEXT);
        hits.add(bx, by, bw, 16, action, null, List.of());
        return by + 16;
    }
}
