package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialDetailRequest;
import dev.drimoz.materialnexus.network.MatrixPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.drimoz.materialnexus.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sorting duplicates (MNX-053, MNX-099): every form still to decide, one at a time. Each candidate is a card with its
 * name, mod and use in recipes, its key shown on it, and its actions as buttons. 1-9 keeps that item and moves on,
 * Shift + number marks it "not the same", S / right skips, B / left goes back. Choices are the same pending changes as
 * anywhere else: nothing is written before Review and apply.
 */
final class TriagePanel {
    private record Item(String material, String form) { }

    private final Runnable exit;
    private final Runnable review;
    private final Ui.Hits hits = new Ui.Hits();
    private final List<Item> queue = new ArrayList<>();
    private int index;
    private MaterialDetailPayload detail;

    TriagePanel(Runnable exit, Runnable review) {
        this.exit = exit;
        this.review = review;
    }

    /** Builds the queue from the grid's "to decide" cells, skipping forms that already have a pending choice. */
    void start(MatrixPayload matrix) {
        queue.clear();
        index = 0;
        detail = null;
        for (int r = 0; r < matrix.materials().size(); r++) {
            for (int c = 0; c < matrix.forms().size(); c++) {
                String material = matrix.materials().get(r);
                String form = matrix.forms().get(c);
                if (matrix.cell(r, c) == 2 && PendingChanges.get(material, form).isEmpty()) queue.add(new Item(material, form));
            }
        }
        request();
    }

    private Optional<Item> current() {
        return index < queue.size() ? Optional.of(queue.get(index)) : Optional.empty();
    }

    private void request() {
        current().ifPresent(item -> {
            if (detail == null || !detail.material().equals(item.material())) {
                PacketDistributor.sendToServer(new MaterialDetailRequest(item.material(), false));
            }
        });
    }

    void accept(MaterialDetailPayload payload) {
        if (current().map(i -> i.material().equals(payload.material())).orElse(false)) detail = payload;
    }

    private void move(int delta) {
        index = net.minecraft.util.Mth.clamp(index + delta, 0, queue.size());
        request();
    }

    private Optional<ResolvedForm> resolved() {
        Optional<Item> item = current();
        if (item.isEmpty() || detail == null || !detail.material().equals(item.get().material())) return Optional.empty();
        return detail.forms().stream().filter(v -> v.form().equals(item.get().form())).findFirst().map(MaterialDetailPayload.FormView::resolved);
    }

    /** Candidates of the current form: the suggestion first, then the alternatives. */
    private List<ResourceLocation> candidates() {
        List<ResourceLocation> list = new ArrayList<>();
        resolved().ifPresent(f -> {
            f.canonical().ifPresent(list::add);
            list.addAll(f.alternatives());
        });
        return list;
    }

    private void pick(int n, boolean notSame) {
        List<ResourceLocation> list = candidates();
        if (n < 0 || n >= list.size()) return;
        Item item = current().orElseThrow();
        if (notSame) {
            PendingChanges.toggleNotSame(list.get(n));
            return;
        }
        PendingChanges.set(item.material(), item.form(), list.get(n));
        move(1);
    }

    boolean key(int key) {
        if (key >= 49 && key <= 57) {
            pick(key - 49, Screen.hasShiftDown());
            return true;
        }
        switch (key) {
            case 83, 262 -> move(1);
            case 66, 263 -> move(-1);
            case 256 -> exit.run();
            default -> { return false; }
        }
        return true;
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus.triage." + key, args);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        int queueWidth = w >= 700 ? 260 : 0;
        int mw = Math.min(720, w - queueWidth - (queueWidth > 0 ? 24 : 0));
        Optional<Item> item = current();
        if (queue.isEmpty() || item.isEmpty()) {
            // Nothing left: say so, and offer what comes next.
            Ui.mark(g, Ui.Status.UNIFIED, x, y + 1);
            g.drawString(font, tr(queue.isEmpty() ? "empty" : "done", queue.size()).copy().withStyle(ChatFormatting.BOLD), x + 12, y, Theme.TEXT, false);
            int bx = x;
            if (PendingChanges.size() > 0) bx = button(g, font, Component.translatable("screen.materialnexus.preview_short"), bx, y + 20, true, mx, my, review);
            button(g, font, tr("back"), bx, y + 20, false, mx, my, exit);
            return;
        }
        int done = (int) queue.stream().filter(i -> PendingChanges.get(i.material(), i.form()).isPresent()).count();
        g.drawString(font, tr("position", index + 1, queue.size()), x, y, Theme.MUTED, false);
        Component chosen = tr("chosen", done);
        g.drawString(font, chosen, x + mw - font.width(chosen), y, Theme.MUTED, false);
        g.fill(x, y + 12, x + mw, y + 16, Theme.RAISED);
        g.fill(x, y + 12, x + mw * done / queue.size(), y + 16, Theme.ACCENT);

        g.drawString(font, tr("item", Names.material(item.get().material()), Names.form(item.get().form())).copy().withStyle(ChatFormatting.BOLD), x, y + 28, Theme.TEXT, false);
        List<ResourceLocation> list = candidates();
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y + 44, Theme.MUTED, false);
            renderQueue(g, font, x + w - queueWidth, y, queueWidth, h, mx, my);
            return;
        }
        g.drawString(font, tr("ask", list.size()), x, y + 40, Theme.MUTED, false);

        Optional<ResourceLocation> pending = PendingChanges.get(item.get().material(), item.get().form());
        int cardWidth = Math.clamp((mw - 16) / 3, 150, 232);
        int cx = x;
        int cy = y + 58;
        for (int i = 0; i < list.size() && i < 9; i++) {
            if (cx + cardWidth > x + mw && cx > x) {
                cx = x;
                cy += 118;
            }
            card(g, font, list.get(i), i, pending.map(list.get(i)::equals).orElse(false), cx, cy, cardWidth, mx, my);
            cx += cardWidth + 8;
        }
        cy += 126;

        // What is left alone is shown, with its reason: it is not a candidate unless picked in the Materials view.
        List<ResolvedForm.NotUnified> aside = resolved().map(ResolvedForm::notUnified).orElse(List.of());
        if (!aside.isEmpty() && cy + 34 < y + h - 24) {
            g.drawString(font, Component.translatable("screen.materialnexus.insp.left_alone", aside.size()), x, cy, Theme.MUTED, false);
            cy += 12;
            for (ResolvedForm.NotUnified n : aside) {
                if (cy + 22 > y + h - 24) break;
                g.fill(x, cy, x + mw, cy + 22, Theme.SURFACE);
                Ui.slot(g, n.item(), x + 4, cy + 2, 0, true);
                String name = Names.stack(n.item()).getHoverName().getString();
                g.drawString(font, name, x + 28, cy + 7, Theme.TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(" · " + NexusScreen.modName(n.item().getNamespace()) + " · "
                        + Component.translatable(n.reasonKey(), n.reasonArgs().toArray()).getString(), mw - 36 - font.width(name)), x + 28 + font.width(name), cy + 7, Theme.MUTED, false);
                cy += 24;
            }
        }

        // The keys, as keys.
        int kx = x;
        int ky = y + h - 14;
        kx = keys(g, font, kx, ky, tr("key.keep"), "1", "2", "3");
        kx = keys(g, font, kx, ky, tr("key.not_same"), "Shift");
        kx = keys(g, font, kx, ky, tr("key.skip"), "S", "→");
        kx = keys(g, font, kx, ky, tr("key.back"), "B", "←");
        keys(g, font, kx, ky, tr("key.leave"), "Esc");

        renderQueue(g, font, x + w - queueWidth, y, queueWidth, h, mx, my);
        hits.tooltip(g, font, mx, my);
    }

    private void card(GuiGraphics g, Font font, ResourceLocation candidate, int i, boolean chosen, int cx, int cy, int cw, int mx, int my) {
        g.fill(cx, cy, cx + cw, cy + 110, Theme.SURFACE);
        if (chosen) g.renderOutline(cx, cy, cw, 110, Theme.INFO);
        // The item, twice its size, on the slot well.
        g.fill(cx + 8, cy + 8, cx + 44, cy + 44, Theme.WELL);
        g.pose().pushPose();
        g.pose().translate(cx + 10, cy + 10, 0);
        g.pose().scale(2, 2, 1);
        g.renderItem(Names.stack(candidate), 0, 0);
        g.pose().popPose();
        String key = String.valueOf(i + 1);
        int kw = font.width(key) + 8;
        g.renderOutline(cx + cw - 8 - kw, cy + 8, kw, 14, Theme.CTRL);
        g.drawString(font, key, cx + cw - 4 - kw, cy + 11, Theme.TEXT, false);
        Component tag = chosen ? Ui.Status.PENDING.label() : i == 0 ? Component.translatable("screen.materialnexus.insp.suggested") : Component.empty();
        g.drawString(font, tag, cx + cw - 14 - kw - font.width(tag), cy + 11, chosen ? Theme.INFO : Theme.WARN, false);

        g.drawString(font, Component.literal(font.plainSubstrByWidth(Names.stack(candidate).getHoverName().getString(), cw - 16)).withStyle(ChatFormatting.BOLD), cx + 8, cy + 50, Theme.TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(NexusScreen.modName(candidate.getNamespace()), cw - 16), cx + 8, cy + 61, Theme.MUTED, false);
        var usage = detail.usage().get(candidate);
        if (usage != null) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.insp.usage", usage.produced(), usage.used()).getString(), cw - 16),
                    cx + 8, cy + 72, Theme.MUTED, false);
        }
        int n = i;
        int bx = button(g, font, Component.translatable("screen.materialnexus.insp.keep"), cx + 8, cy + 88, false, mx, my, () -> pick(n, false));
        boolean notSame = PendingChanges.notSame(candidate);
        Component label = Component.translatable(notSame ? "screen.materialnexus.insp.not_same.undo" : "screen.materialnexus.triage.not_same");
        if (bx - 4 + font.width(label) + 12 <= cx + cw - 8) button(g, font, label, bx - 4, cy + 88, false, mx, my, () -> pick(n, true));
        hits.add(cx + 8, cy + 8, 36, 36, () -> pick(n, false), () -> pick(n, true), List.of(Names.stack(candidate).getHoverName(),
                Component.literal(candidate.toString()).withColor(Theme.TIP_FAINT)));
    }

    /** The whole queue, so the player sees how much is left and what was skipped. */
    private void renderQueue(GuiGraphics g, Font font, int qx, int qy, int qw, int qh, int mx, int my) {
        if (qw == 0) return;
        g.fill(qx - 12, qy - 12, qx - 11, qy + qh + 8, Theme.BORDER);
        g.drawString(font, tr("queue", queue.size()).copy().withStyle(ChatFormatting.BOLD), qx, qy, Theme.TEXT, false);
        int rows = (qh - 20) / 16;
        int first = Math.clamp(index - rows / 2, 0, Math.max(0, queue.size() - rows));
        for (int i = first; i < Math.min(queue.size(), first + rows); i++) {
            Item it = queue.get(i);
            int ry = qy + 18 + (i - first) * 16;
            boolean decided = PendingChanges.get(it.material(), it.form()).isPresent();
            boolean over = mx >= qx && mx < qx + qw && my >= ry && my < ry + 16;
            if (i == index) g.fill(qx, ry, qx + qw, ry + 16, Theme.SEL);
            else if (over) g.fill(qx, ry, qx + qw, ry + 16, Theme.RAISED);
            Ui.mark(g, decided ? Ui.Status.PENDING : Ui.Status.SUGGESTION, qx + 4, ry + 5);
            g.drawString(font, font.plainSubstrByWidth(tr("item", Names.material(it.material()), Names.form(it.form())).getString(), qw - 24), qx + 16, ry + 4,
                    i == index ? Theme.TEXT : Theme.MUTED, false);
            int target = i;
            hits.add(qx, ry, qw, 16, () -> { index = target; request(); }, null, List.of());
        }
    }

    private int keys(GuiGraphics g, Font font, int kx, int ky, Component what, String... caps) {
        for (String cap : caps) {
            int kw = font.width(cap) + 6;
            g.renderOutline(kx, ky - 2, kw, 12, Theme.CTRL);
            g.drawString(font, cap, kx + 3, ky, Theme.TEXT, false);
            kx += kw + 2;
        }
        g.drawString(font, what, kx + 2, ky, Theme.MUTED, false);
        return kx + font.width(what) + 14;
    }

    private int button(GuiGraphics g, Font font, Component label, int bx, int by, boolean primary, int mx, int my, Runnable action) {
        int bw = font.width(label) + 12;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        Ui.button(g, font, label, bx, by, bw, 16, primary, over, true);
        hits.add(bx, by, bw, 16, action, null, List.of());
        return bx + bw + 8;
    }
}
