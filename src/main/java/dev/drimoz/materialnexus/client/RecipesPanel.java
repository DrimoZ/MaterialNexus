package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.datapack.RecipeFamilies;
import dev.drimoz.materialnexus.datapack.RecipeFamilies.Status;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.RecipeFamilyPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Recipes" tab of a material (MNX-009, MNX-088): every loaded recipe making one form, grouped by what applying does
 * to it. The recipe itself is drawn by the installed viewer (JEI) in the inspector of the selected row; Material Nexus
 * only says what it changes. A disabled recipe that is already applied is no longer loaded: this list is then the only
 * place it appears.
 */
final class RecipesPanel {
    private static final List<Status> ORDER = List.of(Status.REWRITTEN, Status.DISABLED, Status.REMOVED, Status.PACK_DISABLED, Status.UNSUPPORTED, Status.ALTERNATIVE, Status.CANONICAL, Status.OTHER);

    private final Consumer<String> requestFamily;
    private final Ui.Hits hits = new Ui.Hits();
    private MaterialDetailPayload detail;
    private RecipeFamilyPayload family;
    private RecipeFamilies.Row selected;
    private String form;
    private int x, y, width, height;
    private double scroll;
    private int content, view;

    RecipesPanel(Consumer<String> requestFamily) {
        this.requestFamily = requestFamily;
    }

    void show(MaterialDetailPayload detail) {
        if (this.detail == null || !this.detail.material().equals(detail.material())) {
            family = null;
            form = null;
            selected = null;
            scroll = 0;
        }
        this.detail = detail;
    }

    void select(String form) {
        this.form = form;
        this.family = null;
        this.selected = null;
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
        scroll = Math.clamp(scroll - delta * Theme.ROW_ITEM, 0, Math.max(0, content - view));
    }

    boolean click(double mx, double my) {
        return hits.click(mx, my, 0);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus.recipes." + key, args);
    }

    /** The viewer's name for the machine when it can lay the recipe out, else the recipe type read as words. */
    private static String machine(RecipeFamilies.Row row) {
        Optional<RecipeViewer.Layout> layout = RecipeHover.layout(row.recipe());
        if (layout.isPresent()) return layout.get().title().getString();
        String words = row.type().getPath().replace('_', ' ').replace('/', ' ');
        return words.isEmpty() ? row.type().toString() : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (detail == null) return;
        // The forms of the material, as chips that wrap: every one can be reached.
        int cx = x;
        int cy = y + 4;
        for (MaterialDetailPayload.FormView v : detail.forms()) {
            Component name = Names.form(v.form());
            int w = font.width(name) + 12;
            if (cx + w > x + width && cx > x) {
                cx = x;
                cy += 20;
            }
            boolean active = v.form().equals(form);
            boolean over = mx >= cx && mx < cx + w && my >= cy && my < cy + 18;
            g.fill(cx, cy, cx + w, cy + 18, active ? Theme.SEL : over ? Theme.RAISED : Theme.SURFACE);
            g.drawString(font, name, cx + 6, cy + 5, active || over ? Theme.TEXT : Theme.MUTED, false);
            String target = v.form();
            hits.add(cx, cy, w, 18, () -> select(target), null, List.of());
            cx += w + 4;
        }
        int top = cy + 26;
        if (form == null) {
            g.drawString(font, tr("select_form"), x, top, Theme.MUTED, false);
            return;
        }
        if (family == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, top, Theme.MUTED, false);
            return;
        }
        if (family.rows().isEmpty()) {
            g.drawString(font, tr("empty"), x, top, Theme.MUTED, false);
            return;
        }
        if (selected == null || !family.rows().contains(selected)) {
            selected = ORDER.stream().flatMap(s -> family.rows().stream().filter(r -> r.status() == s)).findFirst().orElse(null);
        }

        boolean wide = width >= 620;
        int iw = wide ? Math.clamp(width / 3, 240, 340) : 0;
        int tw = width - iw - (wide ? 12 : 0);
        int bottom = y + height;
        view = bottom - top;
        boolean inList = my >= top && my < bottom && mx >= x && mx < x + tw;
        g.enableScissor(x, top, x + tw, bottom);
        int ry = top - (int) scroll;
        RecipeFamilies.Row hovered = null;
        for (Status status : ORDER) {
            List<RecipeFamilies.Row> rows = family.rows().stream().filter(r -> r.status() == status).toList();
            if (rows.isEmpty()) continue;
            g.drawString(font, tr("group." + Names.lowerName(status), rows.size()), x + 4, ry + 8, Theme.MUTED, false);
            ry += 20;
            for (RecipeFamilies.Row row : rows) {
                if (ry + Theme.ROW_ITEM > top && ry < bottom) {
                    boolean hover = inList && my >= ry && my < ry + Theme.ROW_ITEM;
                    if (row == selected) g.fill(x, ry, x + tw - 6, ry + Theme.ROW_ITEM, Theme.SEL);
                    else if (hover) g.fill(x, ry, x + tw - 6, ry + Theme.ROW_ITEM, Theme.RAISED);
                    // MNX-117: a removal (or a way back) not applied yet wears the ring, like every pending choice.
                    if (PendingChanges.recipeRemovalPending(row.recipe())) Ui.mark(g, Ui.Status.PENDING, x + 5, ry + 8 + Theme.ROW_PAD);
                    else mark(g, status, x + 5, ry + 8 + Theme.ROW_PAD);
                    Ui.slot(g, row.output(), x + 16, ry + 2 + Theme.ROW_PAD, 0, switchedOff(row));
                    String name = machine(row);
                    g.drawString(font, font.plainSubstrByWidth(name, tw / 2 - 44), x + 40, ry + 7 + Theme.ROW_PAD, Theme.TEXT, false);
                    g.drawString(font, font.plainSubstrByWidth(Names.withMod(row.output()).getString(), tw / 2 - 34), x + tw / 2, ry + 7 + Theme.ROW_PAD, Theme.MUTED, false);
                    // MNX-117: the bin, always shown (R2), on every recipe that can be removed; red once removed.
                    if (row.removal() == RecipeFamilies.Removal.ALLOWED) {
                        int bx = x + tw - 28;
                        int by = ry + (Theme.ROW_ITEM - 16) / 2;
                        boolean onBin = inList && mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
                        boolean removed = PendingChanges.recipeRemoved(row.recipe());
                        Ui.bin(g, bx + 4, by + 3, removed ? Theme.BAD : onBin ? Theme.TEXT : Theme.MUTED);
                        ResourceLocation recipe = row.recipe();
                        if (inList) hits.add(bx, by, 16, 16, () -> PendingChanges.toggleRecipeRemoval(recipe), null, List.of(tr(removed ? "restore" : "remove")));
                    }
                    // The selected row is already drawn in the inspector.
                    if (hover && !(wide && row == selected)) hovered = row;
                    if (inList) hits.add(x, ry, tw - 6, Theme.ROW_ITEM, () -> selected = row, null, List.of(Component.literal(row.recipe().toString()).withColor(Theme.TIP_FAINT)));
                }
                ry += Theme.ROW_ITEM;
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
        if (wide && selected != null) inspector(g, font, selected, x + tw + 12, top, iw, bottom - top, mx, my);
        // MNX-088: the hovered recipe next to the pointer; without a viewer, its id as a tooltip.
        if (hovered == null || !RecipeHover.draw(g, font, hovered.recipe(), dev.drimoz.materialnexus.client.ui.ScreenSettings.get().showIds() ? Component.literal(hovered.recipe().toString()) : null, mx, my)) hits.tooltip(g, font, mx, my);
    }

    /** The item this form keeps, as it will be after Apply. */
    private Optional<ResourceLocation> kept() {
        return detail.forms().stream().filter(v -> v.form().equals(form)).findFirst()
                .flatMap(v -> PendingChanges.get(v.material(), v.form()).filter(p -> !p.equals(dev.drimoz.materialnexus.datapack.CanonicalChange.RESET))
                        .or(() -> v.resolved().canonical()));
    }

    /** The recipe as the viewer draws it, then what Material Nexus does to it. */
    private void inspector(GuiGraphics g, Font font, RecipeFamilies.Row row, int ix, int iy, int iw, int ih, int mx, int my) {
        g.fill(ix, iy, ix + iw, iy + ih, Theme.SURFACE);
        int left = ix + 12;
        int inner = iw - 24;
        int cy = iy + 8;
        g.drawString(font, Component.literal(font.plainSubstrByWidth(machine(row), inner)).withStyle(ChatFormatting.BOLD), left, cy, Theme.TEXT, false);
        cy += 16;
        RecipeViewer viewer = RecipeViewer.Slot.current;
        Optional<RecipeViewer.Layout> layout = RecipeHover.layout(row.recipe());
        if (layout.isPresent()) {
            // Drawn by the viewer itself, on the tray every recipe of the screen sits on.
            cy += RecipeHover.stage(g, layout.get(), left, cy, mx, my) + 12;
        } else {
            Component why = viewer == null ? tr("no_viewer") : tr(row.status() == Status.DISABLED || row.status() == Status.REMOVED || row.status() == Status.PACK_DISABLED ? "not_loaded" : "not_drawable", viewer.name());
            for (var line : font.split(why, inner)) {
                g.drawString(font, line, left, cy, Theme.MUTED, false);
                cy += 10;
            }
            cy += 8;
        }
        mark(g, row.status(), left, cy + 1);
        for (var line : font.split(Component.translatable("screen.materialnexus.family." + Names.lowerName(row.status())), inner - 12)) {
            g.drawString(font, line, left + 12, cy, Theme.TEXT, false);
            cy += 10;
        }
        cy += 6;
        // MNX-117: what Apply will do to it, why it cannot be removed here, who else already changes it.
        List<Component> notes = new java.util.ArrayList<>();
        if (PendingChanges.recipeRemovalPending(row.recipe())) notes.add(tr(PendingChanges.recipeRemoved(row.recipe()) ? "pending_remove" : "pending_restore"));
        // A recipe a datapack switches off is said once, with the datapack's name, below.
        if (row.removal() != RecipeFamilies.Removal.ALLOWED && row.removal() != RecipeFamilies.Removal.PACK) notes.add(tr("remove." + Names.lowerName(row.removal())));
        // MNX-118: a datapack is named as the player knows it, its folder name.
        row.editedBy().ifPresent(by -> notes.add(by.equals("kubejs") ? tr("edited.kubejs")
                : tr(row.status() == Status.PACK_DISABLED ? "edited.pack_off" : "edited.pack", Names.pack(by))));
        for (Component note : notes) {
            for (var line : font.split(note, inner)) {
                g.drawString(font, line, left, cy, Theme.MUTED, false);
                cy += 10;
            }
            cy += 4;
        }
        // What no viewer shows: the item the recipe made (or still makes), struck, next to the one it makes once applied.
        Optional<ResourceLocation> before = row.status() == Status.ALTERNATIVE ? Optional.of(row.output()) : row.replaced();
        Optional<ResourceLocation> after = row.status() == Status.ALTERNATIVE ? kept() : Optional.of(row.output());
        if (before.isPresent() && after.isPresent() && !before.equals(after)) {
            Ui.slot(g, before.get(), left, cy, 0, true);
            g.fill(left, cy + 8, left + 18, cy + 10, Theme.BAD);
            g.drawString(font, "→", left + 22, cy + 5, Theme.MUTED, false);
            Ui.slot(g, after.get(), left + 34, cy, 0, false);
            g.drawString(font, font.plainSubstrByWidth(Names.withMod(after.get()).getString(), inner - 58), left + 58, cy, Theme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(tr("replaced", Names.withMod(before.get())).getString(), inner - 58), left + 58, cy + 10, Theme.MUTED, false);
        } else {
            Ui.slot(g, row.output(), left, cy, 0, false);
            g.drawString(font, font.plainSubstrByWidth(Names.withMod(row.output()).getString(), inner - 26), left + 24, cy + 5, Theme.MUTED, false);
        }
        cy += 28;
        // MNX-101: technical ids are a setting.
        if (dev.drimoz.materialnexus.client.ui.ScreenSettings.get().showIds()) {
            g.drawString(font, font.plainSubstrByWidth(row.recipe().toString(), inner), left, cy, Theme.FAINT, false);
            g.drawString(font, font.plainSubstrByWidth(row.type().toString(), inner), left, cy + 10, Theme.FAINT, false);
        }
        int by = iy + ih - 22;
        int bw = 0;
        if (viewer != null) {
            Component open = tr("open", viewer.name());
            bw = font.width(open) + 12;
            boolean over = mx >= left && mx < left + bw && my >= by && my < by + 16;
            Ui.button(g, font, open, left, by, bw, 16, false, over, true);
            ResourceLocation item = row.output();
            hits.add(left, by, bw, 16, () -> viewer.open(item), null, List.of());
        }
        // MNX-117 (R10): the destructive action on the right, away from the safe one; Review and Apply confirm it.
        boolean removed = PendingChanges.recipeRemoved(row.recipe());
        Component action = tr(removed ? "restore" : "remove");
        int rw = Math.max(48, font.width(action) + 16);
        int rx = ix + iw - 12 - rw;
        int ry = bw > 0 && left + bw + 8 > rx ? by - 22 : by;
        boolean allowed = row.removal() == RecipeFamilies.Removal.ALLOWED;
        boolean over = allowed && mx >= rx && mx < rx + rw && my >= ry && my < ry + 16;
        if (removed || !allowed) Ui.button(g, font, action, rx, ry, rw, 16, false, over, allowed);
        else Ui.dangerButton(g, font, action, rx, ry, rw, 16, over);
        ResourceLocation recipe = row.recipe();
        if (allowed) hits.add(rx, ry, rw, 16, () -> PendingChanges.toggleRecipeRemoval(recipe), null, List.of());
    }

    /** Switched off, or about to be: the output is drawn dimmed. */
    private static boolean switchedOff(RecipeFamilies.Row row) {
        return row.status() == Status.DISABLED || row.status() == Status.PACK_DISABLED || PendingChanges.recipeRemoved(row.recipe())
                || row.status() == Status.REMOVED && !PendingChanges.recipeRemovalPending(row.recipe());
    }

    /** A tick for what is rewritten, a cross for what is switched off, a dot for what is not handled, a ring for what will be rewritten. */
    private static void mark(GuiGraphics g, Status status, int x, int y) {
        switch (status) {
            case REWRITTEN -> Ui.mark(g, Ui.Status.UNIFIED, x, y);
            case UNSUPPORTED -> Ui.mark(g, Ui.Status.SUGGESTION, x, y);
            case ALTERNATIVE -> Ui.mark(g, Ui.Status.PENDING, x, y);
            case DISABLED, REMOVED, PACK_DISABLED -> {
                for (int i = 0; i < 6; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, Theme.BAD);
                    g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, Theme.BAD);
                }
            }
            default -> { }
        }
    }
}
