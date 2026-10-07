package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Forms" tab of {@link NexusScreen}: one card per form showing every provider as an icon. Clicking an icon
 * makes it the pending canonical choice (clicking it again cancels); nothing is written before Preview / Apply.
 */
final class FormsPanel {
    private static final int CARD = 52;
    private static final int GOLD = 0xFFFFCC33;
    private static final int GREEN = 0xFF55FF55;
    private static final int GREY = 0xFF555555;
    private static final int DIM = 0xFF442222;

    private record Hit(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final boolean readOnly;
    private final Consumer<String> openRecipes;
    private final List<Hit> hits = new ArrayList<>();
    private MaterialDetailPayload detail;
    private int x, y, width, height;
    private double scroll;

    FormsPanel(boolean readOnly, Consumer<String> openRecipes) {
        this.readOnly = readOnly;
        this.openRecipes = openRecipes;
    }

    void show(MaterialDetailPayload detail) {
        if (this.detail == null || !this.detail.material().equals(detail.material())) scroll = 0;
        this.detail = detail;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    void scroll(double delta) {
        if (detail == null) return;
        double max = Math.max(0, detail.forms().size() * CARD - height);
        scroll = Math.max(0, Math.min(max, scroll - delta * 18));
    }

    boolean click(double mx, double my) {
        for (Hit hit : hits) {
            if (hit.contains(mx, my) && my >= y && my < y + height) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (detail == null) return;
        g.enableScissor(x, y, x + width, y + height);
        int top = y - (int) scroll;
        for (MaterialDetailPayload.FormView view : detail.forms()) {
            renderCard(g, font, view, top, mx, my);
            top += CARD;
        }
        g.disableScissor();
        hits.stream().filter(h -> h.contains(mx, my) && my >= y && my < y + height && !h.tooltip().isEmpty()).findFirst()
                .ifPresent(h -> g.renderComponentTooltip(font, h.tooltip(), mx, my));
    }

    private void renderCard(GuiGraphics g, Font font, MaterialDetailPayload.FormView view, int top, int mx, int my) {
        ResolvedForm f = view.resolved();
        String material = detail.material();
        Optional<ResourceLocation> pending = PendingChanges.get(material, view.form());
        boolean decided = f.source() != PolicyPrecedence.DEFAULT && !f.alternatives().isEmpty();

        g.fill(x, top, x + width, top + CARD - 4, 0x55000000);
        Component status;
        int statusColor;
        if (pending.isPresent()) { status = Component.translatable("screen.materialnexus.status.pending"); statusColor = GREEN; }
        else if (decided) { status = Component.translatable("screen.materialnexus.status.unified"); statusColor = GREEN; }
        else if (f.canonical().isPresent() && !f.alternatives().isEmpty()) { status = Component.translatable("screen.materialnexus.status.suggestion"); statusColor = GOLD; }
        else if (f.canonical().isEmpty()) { status = Component.translatable("screen.materialnexus.status.nothing"); statusColor = 0xFF888888; }
        else { status = Component.translatable("screen.materialnexus.status.single"); statusColor = 0xFF888888; }

        g.drawString(font, Names.form(view.form()), x + 6, top + 4, 0xFFFFFF);
        int formWidth = font.width(Names.form(view.form()));
        g.drawString(font, status, x + 12 + formWidth, top + 4, statusColor);

        Component recipes = Component.translatable("screen.materialnexus.open_recipes");
        int rx = x + width - font.width(recipes) - 6;
        boolean overRecipes = mx >= rx && mx < x + width && my >= top + 2 && my < top + 14;
        g.drawString(font, recipes, rx, top + 4, overRecipes ? 0xFFFFFF : 0x88AAFF);
        hits.add(new Hit(rx, top + 2, x + width - rx, 12, () -> openRecipes.accept(view.form()), List.of()));

        Component why = Component.translatable(f.reasonKey(), f.reasonArgs().toArray());
        g.drawString(font, font.plainSubstrByWidth(why.getString(), width - 12), x + 6, top + 15, 0x999999);

        int ix = x + 6;
        int iy = top + 27;
        List<ResourceLocation> providers = new ArrayList<>();
        f.canonical().ifPresent(providers::add);
        providers.addAll(f.alternatives());
        for (ResourceLocation item : providers) {
            boolean isCanonical = f.canonical().map(item::equals).orElse(false);
            int border = pending.map(item::equals).orElse(false) ? GREEN : isCanonical && pending.isEmpty() ? GOLD : GREY;
            List<Component> tip = new ArrayList<>(List.of(Names.stack(item).getHoverName(), Component.literal(item.toString()).withColor(0x888888)));
            tip.add(Component.translatable(isCanonical ? "screen.materialnexus.role.canonical" : "screen.materialnexus.role.alternative").withColor(0xAAAAAA));
            if (isCanonical) tip.add(Component.translatable("screen.materialnexus.why", why,
                    Component.translatable("materialnexus.source." + Names.lowerName(f.source())),
                    Component.translatable("materialnexus.confidence." + Names.lowerName(f.confidence()))).withColor(0xAAAAAA));
            ix = icon(g, item, ix, iy, border, tip, () -> choose(view.form(), item, f, decided));
        }
        for (ResolvedForm.NotUnified n : f.notUnified()) {
            List<Component> tip = List.of(Names.stack(n.item()).getHoverName(), Component.literal(n.item().toString()).withColor(0x888888),
                    Component.translatable("screen.materialnexus.role.not_unified", Component.translatable(n.reasonKey(), n.reasonArgs().toArray())).withColor(0xCC8888));
            ix = icon(g, n.item(), ix, iy, DIM, tip, () -> choose(view.form(), n.item(), f, decided));
        }
    }

    private int icon(GuiGraphics g, ResourceLocation item, int ix, int iy, int border, List<Component> tooltip, Runnable action) {
        g.fill(ix - 1, iy - 1, ix + 19, iy + 19, border);
        g.fill(ix, iy, ix + 18, iy + 18, 0xFF202020);
        ItemStack stack = Names.stack(item);
        g.renderItem(stack, ix + 1, iy + 1);
        hits.add(new Hit(ix - 1, iy - 1, 20, 20, action, tooltip));
        return ix + 22;
    }

    /** Pick an item as the pending canonical; picking the pending one again, or the already decided one, cancels. */
    private void choose(String form, ResourceLocation item, ResolvedForm f, boolean decided) {
        if (readOnly) return;
        String material = detail.material();
        boolean samePending = PendingChanges.get(material, form).map(item::equals).orElse(false);
        boolean alreadyDecided = decided && f.canonical().map(item::equals).orElse(false);
        if (samePending || alreadyDecided) PendingChanges.clear(material, form);
        else PendingChanges.set(material, form, item);
    }
}
