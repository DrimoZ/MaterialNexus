package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialDetailRequest;
import dev.drimoz.materialnexus.network.MatrixPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Triage (MNX-053): every form still to decide, one at a time, its candidates large and numbered. 1-9 keeps that
 * item and moves on, shift + number marks it "not the same", S / right skips, B / left goes back. Choices are the
 * same pending changes as anywhere else: nothing is written before Preview and apply.
 */
final class TriagePanel {
    private record Item(String material, String form) { }

    private final Runnable exit;
    private final Ui.Hits hits = new Ui.Hits();
    private final List<Item> queue = new ArrayList<>();
    private int index;
    private MaterialDetailPayload detail;

    TriagePanel(Runnable exit) {
        this.exit = exit;
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
        index = Math.clamp(index + delta, 0, queue.size());
        request();
    }

    /** Candidates of the current form: the suggestion first, then the alternatives. */
    private List<ResourceLocation> candidates() {
        Optional<ResolvedForm> f = resolved();
        if (f.isEmpty()) return List.of();
        List<ResourceLocation> list = new ArrayList<>();
        f.get().canonical().ifPresent(list::add);
        list.addAll(f.get().alternatives());
        return list;
    }

    private Optional<ResolvedForm> resolved() {
        Optional<Item> item = current();
        if (item.isEmpty() || detail == null || !detail.material().equals(item.get().material())) return Optional.empty();
        return detail.forms().stream().filter(v -> v.form().equals(item.get().form())).findFirst().map(MaterialDetailPayload.FormView::resolved);
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

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        g.drawString(font, Component.translatable("screen.materialnexus.triage.title"), x, y, Ui.TEXT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.triage.keys"), x, y + 12, Ui.FAINT, false);
        Optional<Item> item = current();
        if (queue.isEmpty() || item.isEmpty()) {
            g.drawString(font, Component.translatable(queue.isEmpty() ? "screen.materialnexus.triage.empty" : "screen.materialnexus.triage.done", queue.size()),
                    x, y + 40, Ui.SUCCESS, false);
            return;
        }
        int done = (int) queue.stream().filter(i -> PendingChanges.get(i.material(), i.form()).isPresent()).count();
        g.drawString(font, Component.translatable("screen.materialnexus.triage.progress", index + 1, queue.size(), done), x, y + 32, Ui.MUTED, false);
        g.fill(x, y + 44, x + w, y + 46, 0x30FFFFFF);
        g.fill(x, y + 44, x + w * done / Math.max(1, queue.size()), y + 46, Ui.ACCENT);

        g.pose().pushPose();
        g.pose().translate(x, y + 56, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, Component.translatable("screen.materialnexus.triage.item", Names.material(item.get().material()), Names.form(item.get().form())), 0, 0, Ui.TEXT, false);
        g.pose().popPose();

        List<ResourceLocation> list = candidates();
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y + 84, Ui.MUTED, false);
            return;
        }
        Optional<ResourceLocation> pending = PendingChanges.get(item.get().material(), item.get().form());
        int cx = x;
        int cy = y + 86;
        for (int i = 0; i < list.size() && i < 9; i++) {
            ResourceLocation candidate = list.get(i);
            boolean chosen = pending.map(candidate::equals).orElse(false);
            boolean notSame = PendingChanges.notSame(candidate);
            int box = 96;
            boolean over = mx >= cx && mx < cx + box && my >= cy && my < cy + 84;
            g.fill(cx, cy, cx + box, cy + 84, chosen ? 0x5050A0FF : over ? 0x30FFFFFF : 0x18FFFFFF);
            g.fill(cx, cy, cx + box, cy + 1, chosen ? Ui.ACCENT : i == 0 ? Ui.WARNING : Ui.LINE);
            g.drawString(font, String.valueOf(i + 1), cx + 4, cy + 4, i == 0 ? Ui.WARNING : Ui.MUTED, false);
            g.pose().pushPose();
            g.pose().translate(cx + 32, cy + 12, 0);
            g.pose().scale(2, 2, 1);
            g.renderItem(Names.stack(candidate), 0, 0);
            g.pose().popPose();
            if (notSame) g.drawString(font, Component.translatable("screen.materialnexus.triage.not_same"), cx + 4, cy + 50, Ui.DANGER, false);
            String mod = net.neoforged.fml.ModList.get().getModContainerById(candidate.getNamespace()).map(c -> c.getModInfo().getDisplayName()).orElse(candidate.getNamespace());
            g.drawString(font, font.plainSubstrByWidth(mod, box - 8), cx + 4, cy + 60, Ui.TEXT, false);
            var usage = detail.usage().get(candidate);
            if (usage != null) {
                g.drawString(font, Component.translatable("screen.materialnexus.triage.usage", usage.produced(), usage.used()), cx + 4, cy + 71, Ui.MUTED, false);
            }
            int n = i;
            hits.add(cx, cy, box, 84, () -> pick(n, false), () -> pick(n, true), List.of(Names.stack(candidate).getHoverName(),
                    Component.literal(candidate.toString()).withColor(0x888888)));
            cx += box + 6;
            if (cx + box > x + w) {
                cx = x;
                cy += 90;
            }
        }
        hits.tooltip(g, font, mx, my);
    }
}
