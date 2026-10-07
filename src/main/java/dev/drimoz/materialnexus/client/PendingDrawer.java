package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.CanonicalChange;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pending changes as a side panel (MNX-058): everything not applied yet, grouped (choices by material, process rules,
 * items to create, data edits, settings), each line and each group with its own discard. Built from
 * {@link PendingChanges} at every frame, so a discard shows at once. Nothing is sent to the server.
 */
final class PendingDrawer implements Drawer {
    private static final int LINE = 12;

    private record Line(Component text, int depth, boolean group, Runnable discard) { }

    private final Runnable preview;
    private final Runnable close;
    private final Ui.Hits hits = new Ui.Hits();
    private int x, y, width, height;
    private double scroll;

    PendingDrawer(Runnable preview, Runnable close) {
        this.preview = preview;
        this.close = close;
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

    private static Component group(String key, int count) {
        return Component.translatable("screen.materialnexus.pending.group." + key, count);
    }

    private static List<Line> lines() {
        List<Line> out = new ArrayList<>();
        Map<String, List<CanonicalChange>> byMaterial = new TreeMap<>();
        PendingChanges.all().forEach(c -> byMaterial.computeIfAbsent(c.material(), m -> new ArrayList<>()).add(c));
        if (!byMaterial.isEmpty()) {
            out.add(new Line(group("choices", PendingChanges.all().size()), 0, true,
                    () -> PendingChanges.all().forEach(c -> PendingChanges.clear(c.material(), c.form()))));
            byMaterial.forEach((material, changes) -> {
                out.add(new Line(Component.literal(Names.material(material).getString() + " (" + changes.size() + ")"), 1, true,
                        () -> changes.forEach(c -> PendingChanges.clear(c.material(), c.form()))));
                for (CanonicalChange c : changes) {
                    out.add(new Line(Component.literal(Names.form(c.form()).getString() + " → ").append(c.reset()
                            ? Component.translatable("screen.materialnexus.reset.label") : Names.stack(c.provider()).getHoverName()),
                            2, false, () -> PendingChanges.clear(c.material(), c.form())));
                }
            });
        }
        var processes = new TreeMap<>(PendingChanges.processes()).keySet();
        if (!processes.isEmpty()) {
            out.add(new Line(group("rules", processes.size()), 0, true, () -> processes.forEach(PendingChanges::clearProcess)));
            processes.forEach(form -> out.add(new Line(Component.translatable("screen.materialnexus.preview_process", Names.form(form)), 1, false,
                    () -> PendingChanges.clearProcess(form))));
        }
        List<String> creations = PendingChanges.creations();
        if (!creations.isEmpty()) {
            out.add(new Line(group("items", creations.size()), 0, true, () -> creations.forEach(PendingChanges::toggleCreation)));
            creations.forEach(c -> {
                String[] parts = c.split("/", 2);
                out.add(new Line(Component.translatable("screen.materialnexus.triage.item", Names.material(parts[0]), Names.form(parts[1])), 1, false,
                        () -> PendingChanges.toggleCreation(c)));
            });
        }
        var data = new TreeMap<>(PendingChanges.data()).keySet();
        if (!data.isEmpty()) {
            out.add(new Line(group("data", data.size()), 0, true, () -> data.forEach(PendingChanges::clearData)));
            data.forEach(key -> out.add(new Line(Component.literal(key.replace("|", " · ")), 1, false, () -> PendingChanges.clearData(key))));
        }
        List<Map.Entry<Component, Runnable>> settings = PendingChanges.globalEdits();
        if (!settings.isEmpty()) {
            out.add(new Line(group("settings", settings.size()), 0, true, () -> settings.forEach(e -> e.getValue().run())));
            settings.forEach(e -> out.add(new Line(e.getKey(), 1, false, e.getValue())));
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        g.fill(x, y, x + width, y + height, 0xFF141418);
        g.fill(x, y, x + 1, y + height, Ui.ACCENT);
        g.drawString(font, Component.translatable("screen.materialnexus.pending.title"), x + 8, y + 6, Ui.TEXT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.pending.hint"), x + 8, y + 17, Ui.FAINT, false);

        int listTop = y + 30;
        int listBottom = y + height - 28;
        List<Line> lines = lines();
        scroll = Math.min(scroll, Math.max(0, lines.size() * LINE - (listBottom - listTop)));
        boolean inList = my >= listTop && my < listBottom;
        g.enableScissor(x, listTop, x + width, listBottom);
        int ly = listTop - (int) scroll;
        if (lines.isEmpty()) g.drawString(font, Component.translatable("screen.materialnexus.pending.none"), x + 8, ly, Ui.MUTED, false);
        for (Line line : lines) {
            if (ly + LINE > listTop && ly < listBottom) {
                int lx = x + 8 + line.depth() * 10;
                boolean over = inList && mx >= x && mx < x + width && my >= ly - 1 && my < ly + LINE - 1;
                if (over) g.fill(x + 1, ly - 2, x + width, ly + LINE - 2, 0x14FFFFFF);
                if (line.depth() == 0) g.fill(lx, ly, lx + 2, ly + 8, Ui.ACCENT);
                int tx = lx + (line.depth() == 0 ? 6 : 0);
                g.drawString(font, font.plainSubstrByWidth(line.text().getString(), x + width - 24 - tx), tx, ly,
                        line.group() ? Ui.TEXT : Ui.MUTED, false);
                int bx = x + width - 18;
                boolean overX = inList && mx >= bx && mx < bx + 12 && my >= ly - 2 && my < ly + 10;
                g.drawString(font, "×", bx + 3, ly, overX ? Ui.DANGER : over ? Ui.MUTED : Ui.FAINT, false);
                if (inList) {
                    hits.add(bx, ly - 2, 12, 12, line.discard(), null, List.of(Component.translatable(
                            line.group() ? "screen.materialnexus.pending.discard_group" : "screen.materialnexus.pending.discard")));
                }
            }
            ly += LINE;
        }
        g.disableScissor();

        int by = y + height - 22;
        int bw = (width - 24) / 2;
        button(g, font, mx, my, x + 8, by, bw, Component.translatable("screen.materialnexus.preview_short"), Ui.ACCENT,
                lines.isEmpty() ? null : preview);
        button(g, font, mx, my, x + 16 + bw, by, bw, Component.translatable("screen.materialnexus.pending.close"), Ui.NEUTRAL, close);
        hits.tooltip(g, font, mx, my);
    }

    private void button(GuiGraphics g, Font font, int mx, int my, int bx, int by, int bw, Component label, int color, Runnable action) {
        boolean over = action != null && mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        g.fill(bx, by, bx + bw, by + 16, (color & 0x00FFFFFF) | (action == null ? 0x30000000 : over ? 0xFF000000 : 0xB0000000));
        g.drawCenteredString(font, label, bx + bw / 2, by + 4, action == null ? Ui.FAINT : Ui.TEXT);
        if (action != null) hits.add(bx, by, bw, 16, action, null, List.of());
    }
}
