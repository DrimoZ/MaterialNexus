package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.RecipeFamilies;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.RecipeFamilyPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** "Recipes" tab of {@link NexusScreen}: the recipe family of one form (MNX-009), requested when a form is picked. */
final class RecipesPanel {
    private static final int ROW = 22;

    private record Chip(int x, int y, int w, String form) { }

    private final Consumer<String> requestFamily;
    private final List<Chip> chips = new ArrayList<>();
    private MaterialDetailPayload detail;
    private RecipeFamilyPayload family;
    private String form;
    private int x, y, width, height;
    private double scroll;

    RecipesPanel(Consumer<String> requestFamily) {
        this.requestFamily = requestFamily;
    }

    void show(MaterialDetailPayload detail) {
        if (this.detail == null || !this.detail.material().equals(detail.material())) {
            family = null;
            form = null;
            scroll = 0;
        }
        this.detail = detail;
    }

    void select(String form) {
        this.form = form;
        this.family = null;
        this.scroll = 0;
        requestFamily.accept(form);
    }

    void accept(RecipeFamilyPayload payload) {
        if (detail != null && payload.material().equals(detail.material()) && payload.form().equals(form)) family = payload;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    void scroll(double delta) {
        if (family == null) return;
        double max = Math.max(0, family.rows().size() * ROW - (height - 18));
        scroll = Math.max(0, Math.min(max, scroll - delta * 18));
    }

    boolean click(double mx, double my) {
        for (Chip c : chips) {
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + 12) {
                select(c.form());
                return true;
            }
        }
        return false;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        chips.clear();
        if (detail == null) return;
        int cx = x;
        for (MaterialDetailPayload.FormView view : detail.forms()) {
            Component name = Names.form(view.form());
            int w = font.width(name) + 8;
            if (cx + w > x + width) break;
            boolean active = view.form().equals(form);
            g.fill(cx, y, cx + w, y + 12, active ? 0xFF3A5A8A : 0xFF2A2A2A);
            g.drawString(font, name, cx + 4, y + 2, active ? 0xFFFFFF : 0xAAAAAA);
            chips.add(new Chip(cx, y, w, view.form()));
            cx += w + 4;
        }
        int top = y + 18;
        if (form == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.recipes.select_form"), x, top + 4, 0x888888);
            return;
        }
        if (family == null) return;
        if (family.rows().isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.recipes.empty"), x, top + 4, 0x888888);
            return;
        }
        g.enableScissor(x, top, x + width, y + height);
        int ry = top - (int) scroll;
        for (RecipeFamilies.Row row : family.rows()) {
            g.fill(x, ry, x + width, ry + ROW - 2, 0x40000000);
            g.renderItem(Names.stack(row.output()), x + 2, ry + 2);
            g.drawString(font, font.plainSubstrByWidth(row.recipe().toString(), width - 30), x + 22, ry + 2, 0xFFFFFF);
            Component status = Component.translatable("screen.materialnexus.family." + Names.lowerName(row.status()));
            g.drawString(font, font.plainSubstrByWidth(row.type() + " · " + status.getString(), width - 30), x + 22, ry + 11, color(row.status()));
            ry += ROW;
        }
        g.disableScissor();
    }

    private static int color(RecipeFamilies.Status status) {
        return switch (status) {
            case CANONICAL, REWRITTEN -> 0x55FF55;
            case ALTERNATIVE -> 0xFFCC33;
            case DISABLED -> 0x888888;
            case UNSUPPORTED -> 0xFF6666;
            case OTHER -> 0xAAAAAA;
        };
    }
}
