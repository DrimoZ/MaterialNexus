package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.network.MatrixPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The whole pack at a glance (MNX-049, MNX-098): one row per material, one column per form (most common first). A cell
 * carries a mark as well as a colour, headers read horizontally, and materials with nothing to unify are left out unless
 * asked for. Click a cell or a row to open that material, a column header to open that form.
 */
final class FormMatrix {
    private static final int LABEL = 128;
    private static final int TOOLBAR = 40;
    private static final int HEADER = 24;
    private static final int ROW = 16;

    /** Kept while the game runs: the rows that are grey from end to end are noise by default. */
    private static boolean onlyDuplicates = true;

    private final Consumer<String> openMaterial;
    private final Consumer<String> openForm;
    private final Ui.Hits hits = new Ui.Hits();
    private MatrixPayload data;
    private int x, y, width, height;
    private int firstRow, firstCol;

    FormMatrix(Consumer<String> openMaterial, Consumer<String> openForm) {
        this.openMaterial = openMaterial;
        this.openForm = openForm;
    }

    void accept(MatrixPayload data) { this.data = data; }

    boolean loaded() { return data != null; }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    /** Indices of the materials shown: all of them, or those with at least one duplicated form. */
    private List<Integer> rows() {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < data.materials().size(); r++) {
            boolean duplicate = false;
            for (int c = 0; c < data.forms().size() && !duplicate; c++) duplicate = data.cell(r, c) >= 2;
            if (duplicate || !onlyDuplicates) out.add(r);
        }
        return out;
    }

    private int stride() { return Math.clamp((width - LABEL - 6) / Math.max(1, data.forms().size()), 44, 60); }

    private int visibleRows() { return Math.max(1, (height - TOOLBAR - HEADER - 8) / ROW); }

    private int visibleCols() { return Math.max(1, (width - LABEL - 6) / stride()); }

    void scroll(double delta) {
        if (data == null) return;
        int step = (int) -Math.signum(delta) * 3;
        if (Screen.hasShiftDown()) firstCol = Math.clamp(firstCol + step, 0, Math.max(0, data.forms().size() - visibleCols()));
        else firstRow = Math.clamp(firstRow + step, 0, Math.max(0, rows().size() - visibleRows()));
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (data == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y, Theme.MUTED, false);
            return;
        }
        List<Integer> shown = rows();
        List<String> forms = data.forms();
        int stride = stride();
        firstRow = Math.clamp(firstRow, 0, Math.max(0, shown.size() - visibleRows()));
        firstCol = Math.clamp(firstCol, 0, Math.max(0, forms.size() - visibleCols()));
        int rows = Math.min(visibleRows(), shown.size() - firstRow);
        int cols = Math.min(visibleCols(), forms.size() - firstCol);

        // Toolbar: the filter, then the legend, each state with its mark.
        Component only = Component.translatable("screen.materialnexus.matrix.only");
        Ui.toggle(g, x, y + 4, onlyDuplicates);
        g.drawString(font, only, x + 26, y + 5, Theme.TEXT, false);
        hits.add(x, y, 30 + font.width(only), 18, () -> { onlyDuplicates = !onlyDuplicates; firstRow = 0; }, null, List.of());
        Component count = Component.translatable("screen.materialnexus.matrix.count", shown.size(), data.materials().size(), forms.size());
        g.drawString(font, count, x + width - font.width(count), y + 5, Theme.MUTED, false);
        int lx = x;
        for (Ui.Status s : List.of(Ui.Status.SUGGESTION, Ui.Status.PENDING, Ui.Status.UNIFIED, Ui.Status.SINGLE)) {
            if (s == Ui.Status.SINGLE) g.fill(lx, y + 23, lx + 8, y + 31, Theme.RAISED);
            else Ui.mark(g, s, lx + 1, y + 24);
            g.drawString(font, s.label(), lx + 12, y + 23, Theme.MUTED, false);
            lx += font.width(s.label()) + 24;
        }

        int gx = x + LABEL;
        int gy = y + TOOLBAR + HEADER;
        int overRow = my >= gy && my < gy + rows * ROW && mx >= x && mx < gx + cols * stride ? (my - gy) / ROW : -1;
        int overCol = mx >= gx && mx < gx + cols * stride && my >= y + TOOLBAR && my < gy + rows * ROW ? (mx - gx) / stride : -1;

        // Column headers, horizontal, on two lines when the name is long.
        for (int c = 0; c < cols; c++) {
            String form = forms.get(firstCol + c);
            int cx = gx + c * stride;
            if (c == overCol) g.fill(cx, y + TOOLBAR, cx + stride - 1, gy, Theme.RAISED);
            var lines = font.split(Names.form(form), stride - 4);
            int count2 = Math.min(2, lines.size());
            for (int i = 0; i < count2; i++) {
                g.drawString(font, lines.get(i), cx + 2, gy - 2 - (count2 - i) * 10, c == overCol ? Theme.TEXT : Theme.MUTED, false);
            }
            hits.add(cx, y + TOOLBAR, stride, HEADER, () -> openForm.accept(form), null, List.of(Names.form(form)));
        }

        for (int r = 0; r < rows; r++) {
            int index = shown.get(firstRow + r);
            String material = data.materials().get(index);
            int ry = gy + r * ROW;
            if (r == overRow) g.fill(x, ry, gx - 1, ry + ROW - 1, Theme.RAISED);
            g.drawString(font, font.plainSubstrByWidth(Names.material(material).getString(), LABEL - 8), x + 4, ry + 4, r == overRow ? Theme.TEXT : Theme.MUTED, false);
            hits.add(x, ry, LABEL, ROW, () -> openMaterial.accept(material), null, List.of());
            for (int c = 0; c < cols; c++) {
                String form = forms.get(firstCol + c);
                int cell = data.cell(index, firstCol + c);
                int cx = gx + c * stride;
                if (cell == 0) continue;
                boolean pending = PendingChanges.get(material, form).isPresent();
                Ui.Status status = pending ? Ui.Status.PENDING : switch (cell) { case 2 -> Ui.Status.SUGGESTION; case 3 -> Ui.Status.UNIFIED; default -> Ui.Status.SINGLE; };
                int fill = status == Ui.Status.SINGLE ? Theme.RAISED : Theme.mix(Theme.BG, status.color(), 0.28f);
                g.fill(cx, ry, cx + stride - 1, ry + ROW - 1, fill);
                Ui.mark(g, status, cx + (stride - 7) / 2, ry + 4);
                if (r == overRow && c == overCol) g.renderOutline(cx, ry, stride - 1, ROW - 1, Theme.TEXT);
                hits.add(cx, ry, stride - 1, ROW - 1, () -> openMaterial.accept(material), null,
                        List.of(Component.translatable("screen.materialnexus.matrix.cell", Names.material(material), Names.form(form), status.label())));
            }
        }

        // Scrollbars, shown when there is more than fits; shift + wheel moves the columns.
        if (shown.size() > rows) {
            int track = rows * ROW;
            int bar = Math.max(12, track * rows / shown.size());
            g.fill(x + width - 4, gy, x + width, gy + track, Theme.RAISED);
            g.fill(x + width - 4, gy + (track - bar) * firstRow / (shown.size() - rows), x + width, gy + (track - bar) * firstRow / (shown.size() - rows) + bar, Theme.CTRL);
        }
        if (forms.size() > cols) {
            int track = cols * stride;
            int bar = Math.max(12, track * cols / forms.size());
            int by = gy + rows * ROW + 2;
            g.fill(gx, by, gx + track, by + 4, Theme.RAISED);
            g.fill(gx + (track - bar) * firstCol / (forms.size() - cols), by, gx + (track - bar) * firstCol / (forms.size() - cols) + bar, by + 4, Theme.CTRL);
        }
        hits.tooltip(g, font, mx, my);
    }
}
