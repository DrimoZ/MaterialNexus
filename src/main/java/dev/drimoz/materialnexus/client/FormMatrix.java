package dev.drimoz.materialnexus.client;

import com.mojang.math.Axis;
import dev.drimoz.materialnexus.network.MatrixPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * The whole pack at a glance (MNX-049): one row per material, one column per form (most common first), each cell
 * colored by status. Click a cell or a row to open that material, a column header to open that form. Wheel scrolls
 * rows, shift + wheel scrolls columns.
 */
final class FormMatrix {
    private static final int LABEL = 96;
    private static final int HEADER = 70;
    /** Columns grow to use the width (12 to 26 px); rows stay compact so many materials fit. */
    private int stride = 12;
    private static final int ROW = 13;

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

    void accept(MatrixPayload data) {
        this.data = data;
        relayout();
    }

    private void relayout() {
        if (data != null && !data.forms().isEmpty()) stride = net.minecraft.util.Mth.clamp((width - LABEL) / data.forms().size(), 12, 26);
    }

    boolean loaded() { return data != null; }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        relayout();
    }

    private int visibleRows() { return Math.max(1, (height - HEADER - 14) / ROW); }

    private int visibleCols() { return Math.max(1, (width - LABEL) / stride); }

    void scroll(double delta) {
        if (data == null) return;
        int step = (int) -Math.signum(delta) * 3;
        if (Screen.hasShiftDown()) firstCol = net.minecraft.util.Mth.clamp(firstCol + step, 0, Math.max(0, data.forms().size() - visibleCols()));
        else firstRow = net.minecraft.util.Mth.clamp(firstRow + step, 0, Math.max(0, data.materials().size() - visibleRows()));
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    private static int color(int cell, boolean pending) {
        if (pending) return Ui.ACCENT;
        return switch (cell) {
            case 1 -> Ui.NEUTRAL;
            case 2 -> Ui.WARNING;
            case 3 -> Ui.SUCCESS;
            default -> 0x18FFFFFF;
        };
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (data == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y, Ui.MUTED, false);
            return;
        }
        List<String> materials = data.materials();
        List<String> forms = data.forms();
        int rows = Math.min(visibleRows(), materials.size() - firstRow);
        int cols = Math.min(visibleCols(), forms.size() - firstCol);

        // Column headers, written bottom-up.
        for (int c = 0; c < cols; c++) {
            String form = forms.get(firstCol + c);
            int cx = x + LABEL + c * stride;
            boolean over = mx >= cx && mx < cx + stride - 1 && my >= y && my < y + HEADER;
            g.pose().pushPose();
            g.pose().translate(cx + 2, y + HEADER - 3, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
            g.drawString(font, font.plainSubstrByWidth(Names.form(form).getString(), HEADER - 6), 0, 0, over ? Ui.TEXT : Ui.MUTED, false);
            g.pose().popPose();
            hits.add(cx, y, stride, HEADER, () -> openForm.accept(form), null, List.of(Names.form(form)));
        }

        for (int r = 0; r < rows; r++) {
            String material = materials.get(firstRow + r);
            int ry = y + HEADER + r * ROW;
            boolean rowOver = my >= ry && my < ry + ROW && mx >= x && mx < x + LABEL + cols * stride;
            if (rowOver) g.fill(x, ry - 1, x + LABEL + cols * stride, ry + ROW, 0x14FFFFFF);
            g.drawString(font, font.plainSubstrByWidth(Names.material(material).getString(), LABEL - 6), x + 2, ry + 3, rowOver ? Ui.TEXT : Ui.MUTED, false);
            hits.add(x, ry, LABEL, ROW, () -> openMaterial.accept(material), null, List.of());
            for (int c = 0; c < cols; c++) {
                String form = forms.get(firstCol + c);
                int cell = data.cell(firstRow + r, firstCol + c);
                boolean pending = cell != 0 && PendingChanges.get(material, form).isPresent();
                int cx = x + LABEL + c * stride;
                g.fill(cx, ry, cx + stride - 1, ry + ROW - 1, color(cell, pending));
                if (cell == 0) continue;
                Ui.Status status = pending ? Ui.Status.PENDING : switch (cell) { case 2 -> Ui.Status.SUGGESTION; case 3 -> Ui.Status.UNIFIED; default -> Ui.Status.SINGLE; };
                hits.add(cx, ry, stride - 1, ROW - 1, () -> openMaterial.accept(material), null,
                        List.of(Component.translatable("screen.materialnexus.matrix.cell", Names.material(material), Names.form(form), status.label())));
            }
        }

        int ly = y + HEADER + rows * ROW + 4;
        int lx = x;
        for (Ui.Status s : List.of(Ui.Status.SUGGESTION, Ui.Status.UNIFIED, Ui.Status.SINGLE, Ui.Status.PENDING)) {
            g.fill(lx, ly + 1, lx + 7, ly + 8, s.color);
            g.drawString(font, s.label(), lx + 10, ly, Ui.MUTED, false);
            lx += font.width(s.label()) + 20;
        }
        g.drawString(font, Component.translatable("screen.materialnexus.matrix.position", firstRow + 1, firstRow + rows, materials.size(),
                firstCol + 1, firstCol + cols, forms.size()), lx + 6, ly, Ui.FAINT, false);
        hits.tooltip(g, font, mx, my);
    }
}
