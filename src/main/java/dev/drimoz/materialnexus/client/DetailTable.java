package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.CanonicalChange;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.item.CreatedItems;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The material (or form) detail of the redesigned screen (MNX-049): one row per form (or per material), with its
 * status, the kept item, the alternatives and the variants set aside. Click an item to make it the pending choice
 * (again to cancel); for a material, forms it lacks are listed last with "Create item".
 */
final class DetailTable {
    private static final int ROW = 26;
    private static final int HEAD = 13;
    private static final int LABEL = 112;
    private static final int STATUS = 84;

    private final boolean readOnly;
    private final BiConsumer<String, String> openRecipes;
    private final Ui.Hits hits = new Ui.Hits();
    private MaterialDetailPayload detail;
    private int x, y, width, height;
    private double scroll;

    DetailTable(boolean readOnly, BiConsumer<String, String> openRecipes) {
        this.readOnly = readOnly;
        this.openRecipes = openRecipes;
    }

    void show(MaterialDetailPayload detail) {
        if (this.detail == null || !this.detail.material().equals(detail.material()) || this.detail.byForm() != detail.byForm()) scroll = 0;
        this.detail = detail;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    private List<String> creatable() {
        if (detail == null || detail.byForm()) return List.of();
        Set<FormId> present = new HashSet<>();
        detail.forms().forEach(v -> present.add(new FormId(v.form())));
        return CreatedItems.creatable(present).stream().map(FormId::name).toList();
    }

    void scroll(double delta) {
        if (detail == null) return;
        double max = Math.max(0, HEAD + (detail.forms().size() + creatable().size()) * ROW - height);
        scroll = Math.clamp(scroll - delta * ROW, 0, max);
    }

    boolean click(double mx, double my, int button) {
        return my >= y && my < y + height && hits.click(mx, my, button);
    }

    static Ui.Status status(MaterialDetailPayload.FormView view) {
        ResolvedForm f = view.resolved();
        Optional<ResourceLocation> pending = PendingChanges.get(view.material(), view.form());
        if (pending.isPresent()) return pending.get().equals(CanonicalChange.RESET) ? Ui.Status.RESET : Ui.Status.PENDING;
        if (f.alternatives().isEmpty()) return f.canonical().isPresent() ? Ui.Status.SINGLE : Ui.Status.NOTHING;
        return f.source() == PolicyPrecedence.DEFAULT ? Ui.Status.SUGGESTION : Ui.Status.UNIFIED;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (detail == null) return;
        g.drawString(font, Component.translatable(detail.byForm() ? "screen.materialnexus.col.material" : "screen.materialnexus.col.form"), x + 4, y + 2, Ui.FAINT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.col.status"), x + LABEL, y + 2, Ui.FAINT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.col.kept"), x + LABEL + STATUS, y + 2, Ui.FAINT, false);
        g.drawString(font, Component.translatable("screen.materialnexus.col.others"), x + LABEL + STATUS + 30, y + 2, Ui.FAINT, false);
        g.enableScissor(x, y + HEAD, x + width, y + height);
        int top = y + HEAD - (int) scroll;
        for (MaterialDetailPayload.FormView view : detail.forms()) {
            if (top + ROW > y + HEAD && top < y + height) row(g, font, view, top, mx, my);
            top += ROW;
        }
        for (String form : creatable()) {
            if (top + ROW > y + HEAD && top < y + height) absentRow(g, font, form, top, mx, my);
            top += ROW;
        }
        g.disableScissor();
        if (my >= y + HEAD && my < y + height) hits.tooltip(g, font, mx, my);
    }

    private void row(GuiGraphics g, Font font, MaterialDetailPayload.FormView view, int top, int mx, int my) {
        ResolvedForm f = view.resolved();
        String material = view.material();
        boolean hover = my >= top && my < top + ROW && mx >= x && mx < x + width;
        g.fill(x, top + ROW - 1, x + width, top + ROW, Ui.LINE);
        if (hover) g.fill(x, top, x + width, top + ROW - 1, 0x18FFFFFF);
        Component label = detail.byForm() ? Names.material(material) : Names.form(view.form());
        g.drawString(font, font.plainSubstrByWidth(label.getString(), LABEL - 8), x + 4, top + 9, Ui.TEXT, false);

        Ui.Status status = status(view);
        Ui.pill(g, font, status.label(), x + LABEL, top + 7, status.color);

        Optional<ResourceLocation> pending = PendingChanges.get(material, view.form()).filter(p -> !p.equals(CanonicalChange.RESET));
        boolean decided = f.source() != PolicyPrecedence.DEFAULT && !f.alternatives().isEmpty();
        Component why = Component.translatable(f.reasonKey(), f.reasonArgs().toArray());
        int sx = x + LABEL + STATUS + 4;
        Optional<ResourceLocation> kept = pending.or(f::canonical);
        if (kept.isPresent()) {
            Ui.slot(g, kept.get(), sx, top + 4, status.color, false);
            List<Component> tip = itemTip(kept.get(), Component.translatable(pending.isPresent() ? "screen.materialnexus.role.pending" : "screen.materialnexus.role.canonical"));
            tip.add(Component.translatable("screen.materialnexus.why", why,
                    Component.translatable("materialnexus.source." + Names.lowerName(f.source())),
                    Component.translatable("materialnexus.confidence." + Names.lowerName(f.confidence()))).withColor(0xAAAAAA));
            ResourceLocation k = kept.get();
            notSameMark(g, k, sx, top + 4);
            hits.add(sx - 1, top + 3, 20, 20, () -> choose(material, view.form(), k, f, decided), notSame(k), withHint(tip, k));
        }

        int ix = sx + 26;
        List<ResourceLocation> others = new ArrayList<>();
        f.canonical().filter(c -> !c.equals(kept.orElse(null))).ifPresent(others::add);
        f.alternatives().stream().filter(a -> !a.equals(kept.orElse(null))).forEach(others::add);
        int right = x + width - 64;
        for (ResourceLocation item : others) {
            if (ix + 20 > right) break;
            Ui.slot(g, item, ix, top + 4, Ui.NEUTRAL, false);
            notSameMark(g, item, ix, top + 4);
            hits.add(ix - 1, top + 3, 20, 20, () -> choose(material, view.form(), item, f, decided), notSame(item),
                    withHint(itemTip(item, Component.translatable("screen.materialnexus.role.alternative")), item));
            ix += 21;
        }
        if (!f.notUnified().isEmpty() && ix + 30 < right) {
            ix += 4;
            g.fill(ix - 3, top + 6, ix - 2, top + 20, Ui.LINE);
        }
        for (ResolvedForm.NotUnified n : f.notUnified()) {
            if (ix + 20 > right) break;
            Ui.slot(g, n.item(), ix, top + 4, 0, true);
            List<Component> tip = itemTip(n.item(), Component.translatable("screen.materialnexus.role.not_unified",
                    Component.translatable(n.reasonKey(), n.reasonArgs().toArray())).withColor(0xCC8888));
            notSameMark(g, n.item(), ix, top + 4);
            hits.add(ix - 1, top + 3, 20, 20, () -> choose(material, view.form(), n.item(), f, decided), notSame(n.item()), withHint(tip, n.item()));
            ix += 21;
        }

        Component recipes = Component.translatable("screen.materialnexus.open_recipes");
        int rx = x + width - font.width(recipes) - 6;
        // MNX-058: a saved choice can be put back to default (pending, like any choice); clicking again cancels.
        if (!readOnly && f.source() == PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE) {
            boolean resetting = status == Ui.Status.RESET;
            int bx = rx - 16;
            boolean overR = mx >= bx && mx < bx + 12 && my >= top + 7 && my < top + 19;
            g.drawString(font, "↺", bx + 2, top + 9, resetting ? Ui.WARNING : overR ? Ui.TEXT : Ui.FAINT, false);
            hits.add(bx, top + 7, 12, 12, () -> {
                if (resetting) PendingChanges.clear(material, view.form());
                else PendingChanges.set(material, view.form(), CanonicalChange.RESET);
            }, null, List.of(Component.translatable(resetting ? "screen.materialnexus.reset.cancel" : "screen.materialnexus.reset.row")));
        }
        boolean over = mx >= rx && my >= top && my < top + ROW && mx < x + width;
        g.drawString(font, recipes, rx, top + 9, over ? Ui.TEXT : 0x88AAFF, false);
        hits.add(rx, top, x + width - rx, ROW, () -> openRecipes.accept(material, view.form()), null, List.of());
    }

    private void absentRow(GuiGraphics g, Font font, String form, int top, int mx, int my) {
        String material = detail.material();
        g.fill(x, top + ROW - 1, x + width, top + ROW, Ui.LINE);
        g.drawString(font, Names.form(form), x + 4, top + 9, Ui.FAINT, false);
        Ui.pill(g, font, Ui.Status.ABSENT.label(), x + LABEL, top + 7, Ui.Status.ABSENT.color);
        int sx = x + LABEL + STATUS + 4;
        g.fill(sx, top + 4, sx + 18, top + 22, 0x40FFFFFF);
        g.fill(sx + 1, top + 5, sx + 17, top + 21, 0xFF16161C);
        if (readOnly) return;
        boolean pending = PendingChanges.creating(material, form);
        Component label = Component.translatable(pending ? "screen.materialnexus.create.pending" : "screen.materialnexus.create.button");
        int bx = sx + 26;
        int bw = font.width(label) + 10;
        boolean over = mx >= bx && mx < bx + bw && my >= top + 6 && my < top + 20;
        g.fill(bx, top + 6, bx + bw, top + 20, pending ? 0xFF24502E : over ? 0xFF3A3A55 : 0xFF2A2A3A);
        g.drawString(font, label, bx + 5, top + 9, pending ? 0x55FF55 : 0xAAAAFF, false);
        hits.add(bx, top + 6, bw, 14, () -> PendingChanges.toggleCreation(material, form), null,
                List.of(Component.translatable("screen.materialnexus.create.tooltip", "materialnexus:" + material + "_" + form)));
    }

    /** MNX-050: right click marks an item as "not the same" (or unmarks it); a pending mark is a red cross. */
    private Runnable notSame(ResourceLocation item) {
        return readOnly ? null : () -> PendingChanges.toggleNotSame(item);
    }

    private static void notSameMark(GuiGraphics g, ResourceLocation item, int x, int y) {
        if (!PendingChanges.globalChanged("not_same") || !PendingChanges.notSame(item)) return;
        for (int i = 0; i < 18; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, Ui.DANGER);
            g.fill(x + 17 - i, y + i, x + 18 - i, y + i + 1, Ui.DANGER);
        }
    }

    private List<Component> withHint(List<Component> tip, ResourceLocation item) {
        List<Component> out = new ArrayList<>(tip);
        // MNX-052: what helps choose: its mod, and how central it is in the pack's recipes.
        var usage = detail.usage().get(item);
        String mod = net.neoforged.fml.ModList.get().getModContainerById(item.getNamespace()).map(c -> c.getModInfo().getDisplayName()).orElse(item.getNamespace());
        out.add(usage == null ? Component.translatable("screen.materialnexus.usage.mod", mod).withColor(0x88AACC)
                : Component.translatable("screen.materialnexus.usage", mod, usage.produced(), usage.used()).withColor(0x88AACC));
        if (readOnly) return out;
        out.add(Component.translatable(PendingChanges.notSame(item) ? "screen.materialnexus.not_same.unmark" : "screen.materialnexus.not_same.mark").withColor(0x777788));
        return out;
    }

    private static List<Component> itemTip(ResourceLocation item, Component role) {
        return new ArrayList<>(List.of(Names.stack(item).getHoverName(), Component.literal(item.toString()).withColor(0x888888),
                role.copy().withColor(0xAAAAAA)));
    }

    /** Pick an item as the pending canonical; picking the pending one again, or the already decided one, cancels. */
    private void choose(String material, String form, ResourceLocation item, ResolvedForm f, boolean decided) {
        if (readOnly) return;
        boolean samePending = PendingChanges.get(material, form).map(item::equals).orElse(false);
        boolean alreadyDecided = decided && f.canonical().map(item::equals).orElse(false);
        if (samePending || alreadyDecided) PendingChanges.clear(material, form);
        else PendingChanges.set(material, form, item);
    }
}
