package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.network.ProcessPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * "How it's made" tab of a form view (MNX-036, MNX-098): how this form is made, for every material. The routes of the
 * rule with their quantities, two switches said in plain words with their consequence, and the routes found in the
 * pack's recipes grouped by machine. Edits are a pending change, written only through Review and apply.
 */
final class ProcessPanel {
    private static final int ROW = 20;
    private static final ProcessRules.Rule EMPTY = new ProcessRules.Rule(List.of(), false, false);

    private final boolean readOnly;
    private final Ui.Hits hits = new Ui.Hits();
    private ProcessPayload payload;
    private int x, y, width, height;
    /** Materials of this form whose duplicates are still only suggested: a rule adds recipes, it does not unify items. */
    private int undecided;
    private double scroll;
    private int foundTop, foundContent;

    ProcessPanel(boolean readOnly) {
        this.readOnly = readOnly;
    }

    void accept(ProcessPayload payload) {
        this.payload = payload;
        this.scroll = 0;
    }

    void undecided(int count) { this.undecided = count; }

    boolean shows(String form) { return payload != null && payload.form().equals(form); }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = Math.min(width, 720); this.height = height;
    }

    void scroll(double delta) {
        scroll = net.minecraft.util.Mth.clamp(scroll - delta * ROW, 0, Math.max(0, foundContent - (y + height - foundTop)));
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    private ProcessRules.Rule rule() {
        return PendingChanges.process(payload.form()).orElse(payload.rule().orElse(EMPTY));
    }

    private void edit(UnaryOperator<ProcessRules.Rule> change) {
        ProcessRules.Rule next = change.apply(rule());
        if (next.equals(payload.rule().orElse(EMPTY))) PendingChanges.clearProcess(payload.form());
        else PendingChanges.setProcess(payload.form(), next);
    }

    private void editRoute(int index, UnaryOperator<ProcessRules.Route> change) {
        edit(r -> {
            List<ProcessRules.Route> routes = new ArrayList<>(r.routes());
            if (change == null) routes.remove(index);
            else routes.set(index, change.apply(routes.get(index)));
            return new ProcessRules.Rule(routes, r.exclusive(), r.enforceRatio());
        });
    }

    private static ProcessRules.Route ratio(ProcessRules.Route r, int in, int out) {
        return new ProcessRules.Route(r.machine(), r.input(), net.minecraft.util.Mth.clamp(in, 1, 64), net.minecraft.util.Mth.clamp(out, 1, 64));
    }

    /** A recipe of the pack that follows this route, or the same machine and input (MNX-088); null when the server found none. */
    private ResourceLocation sample(ProcessRules.Route route) {
        List<ProcessRules.Route> routes = payload.examples().routes();
        int at = routes.indexOf(route);
        for (int i = 0; at < 0 && i < routes.size(); i++) {
            if (routes.get(i).machine().equals(route.machine()) && routes.get(i).input().equals(route.input())) at = i;
        }
        return at >= 0 && at < payload.samples().size() ? payload.samples().get(at) : null;
    }

    /** The viewer's name for the machine when it can draw one of its recipes; else "modern_industrialization:forge_hammer" reads "Forge hammer". */
    private String machine(ResourceLocation id) {
        List<ProcessRules.Route> routes = payload.examples().routes();
        for (int i = 0; i < routes.size() && i < payload.samples().size(); i++) {
            if (!routes.get(i).machine().equals(id)) continue;
            var layout = RecipeHover.layout(payload.samples().get(i));
            if (layout.isPresent()) return layout.get().title().getString();
        }
        String text = id.getPath().replace('_', ' ').replace('/', ' ');
        return text.isEmpty() ? id.toString() : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus.process." + key, args);
    }

    void render(GuiGraphics g, Font font, int mx, int my) {
        hits.clear();
        if (payload == null) {
            g.drawString(font, tr("loading"), x, y + 4, Theme.MUTED, false);
            return;
        }
        Component formName = Names.form(payload.form());
        ProcessRules.Rule rule = rule();
        boolean pending = PendingChanges.process(payload.form()).isPresent();
        int cy = y + 4;
        ResourceLocation hovered = null;

        if (pending) {
            g.renderOutline(x, cy + 1, 6, 6, Theme.INFO);
            g.drawString(font, tr("changed"), x + 12, cy, Theme.INFO, false);
            if (!readOnly) button(g, font, tr("cancel"), x + 20 + font.width(tr("changed")), cy - 4, mx, my, () -> PendingChanges.clearProcess(payload.form()));
        } else {
            g.drawString(font, rule.routes().isEmpty() ? tr("none") : tr("title", formName), x, cy, Theme.MUTED, false);
        }
        cy += 16;
        if (undecided > 0) {
            Ui.mark(g, Ui.Status.SUGGESTION, x, cy + 1);
            for (var line : font.split(tr("undecided", undecided, formName), width - 12)) {
                g.drawString(font, line, x + 12, cy, Theme.WARN, false);
                cy += 10;
            }
            cy += 6;
        }

        // The routes of the rule: machine, how much goes in, how much comes out.
        int inX = x + width - 300;
        int outX = x + width - 170;
        g.drawString(font, tr("rule.routes", rule.routes().size()).copy().withStyle(ChatFormatting.BOLD), x, cy + 4, Theme.TEXT, false);
        if (!rule.routes().isEmpty()) {
            g.drawString(font, tr("col.in"), inX, cy + 4, Theme.MUTED, false);
            g.drawString(font, tr("col.out"), outX, cy + 4, Theme.MUTED, false);
        }
        cy += 18;
        for (int i = 0; i < rule.routes().size(); i++) {
            ProcessRules.Route route = rule.routes().get(i);
            int index = i;
            g.fill(x, cy, x + width, cy + ROW - 2, Theme.SURFACE);
            if (mx >= x && mx < inX - 8 && my >= cy && my < cy + ROW - 2) hovered = sample(route);
            String name = machine(route.machine());
            g.drawString(font, font.plainSubstrByWidth(name, inX - x - 16), x + 6, cy + 5, Theme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(" · " + NexusScreen.modName(route.machine().getNamespace()), inX - x - 16 - font.width(name)),
                    x + 6 + font.width(name), cy + 5, Theme.MUTED, false);
            stepper(g, font, inX, cy + 1, route.in(), Names.form(route.input().name()), mx, my,
                    () -> editRoute(index, r -> ratio(r, r.in() - 1, r.out())), () -> editRoute(index, r -> ratio(r, r.in() + 1, r.out())));
            stepper(g, font, outX, cy + 1, route.out(), formName, mx, my,
                    () -> editRoute(index, r -> ratio(r, r.in(), r.out() - 1)), () -> editRoute(index, r -> ratio(r, r.in(), r.out() + 1)));
            if (!readOnly) {
                Component remove = tr("remove");
                button(g, font, remove, x + width - font.width(remove) - 14, cy + 1, mx, my, () -> editRoute(index, null));
            }
            cy += ROW;
        }
        cy += 6;

        // Two switches, each said in plain words with what it does.
        cy = toggle(g, font, cy, mx, my, rule.exclusive(), tr("exclusive.label"), tr(rule.exclusive() ? "exclusive.on" : "exclusive.off", formName),
                () -> edit(r -> new ProcessRules.Rule(r.routes(), !r.exclusive(), r.enforceRatio())));
        cy = toggle(g, font, cy, mx, my, rule.enforceRatio(), tr("enforce.label"), tr(rule.enforceRatio() ? "enforce.on" : "enforce.off"),
                () -> edit(r -> new ProcessRules.Rule(r.routes(), r.exclusive(), !r.enforceRatio())));
        cy += 8;

        // What the pack already does, grouped by machine; a variant can be added to the rule.
        Map<ResourceLocation, List<ProcessRules.Route>> found = new LinkedHashMap<>();
        for (ProcessRules.Route e : payload.examples().routes()) {
            if (rule.routes().stream().anyMatch(r -> r.machine().equals(e.machine()) && r.input().equals(e.input()))) continue;
            found.computeIfAbsent(e.machine(), m -> new ArrayList<>()).add(e);
        }
        g.drawString(font, tr("found").copy().withStyle(ChatFormatting.BOLD), x, cy + 4, Theme.TEXT, false);
        Component count = found.isEmpty() ? tr("no_examples") : tr("found.count", found.size(), found.values().stream().mapToInt(List::size).sum());
        g.drawString(font, count, x + width - font.width(count), cy + 4, Theme.MUTED, false);
        cy += 18;
        foundTop = cy;
        int bottom = y + height;
        g.enableScissor(x, foundTop, x + width, bottom);
        int ly = foundTop - (int) scroll;
        for (var entry : found.entrySet()) {
            String name = machine(entry.getKey());
            if (ly + 16 > foundTop && ly < bottom) {
                g.drawString(font, name, x + 6, ly + 4, Theme.TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(" · " + NexusScreen.modName(entry.getKey().getNamespace())
                                // MNX-101: the recipe type id is a technical id, shown with the setting.
                                + (dev.drimoz.materialnexus.client.ui.ScreenSettings.get().showIds() ? "   " + entry.getKey() : ""), width - 20 - font.width(name)),
                        x + 6 + font.width(name), ly + 4, Theme.FAINT, false);
            }
            ly += 16;
            for (ProcessRules.Route e : entry.getValue()) {
                if (ly + ROW > foundTop && ly < bottom) {
                    if (mx >= x + 18 && mx < x + width - 80 && my >= Math.max(ly, foundTop) && my < Math.min(ly + ROW, bottom)) hovered = sample(e);
                    g.drawString(font, tr("variant", e.in(), Names.form(e.input().name()), e.out(), formName), x + 18, ly + 5, Theme.MUTED, false);
                    if (!readOnly && ly >= foundTop && ly + ROW <= bottom) {
                        Component add = tr("add");
                        button(g, font, add, x + width - font.width(add) - 20, ly + 1, mx, my, () -> edit(r -> {
                            List<ProcessRules.Route> routes = new ArrayList<>(r.routes());
                            routes.add(e);
                            return new ProcessRules.Rule(routes, r.exclusive(), r.enforceRatio());
                        }));
                    }
                }
                ly += ROW;
            }
            ly += 4;
        }
        g.disableScissor();
        foundContent = ly + (int) scroll - foundTop;
        int view = bottom - foundTop;
        scroll = net.minecraft.util.Mth.clamp(scroll, 0, Math.max(0, foundContent - view));
        if (foundContent > view && view > 0) {
            int bar = Math.max(12, view * view / foundContent);
            g.fill(x + width - 4, foundTop, x + width, bottom, Theme.RAISED);
            int at = (int) (scroll * (view - bar) / (foundContent - view));
            g.fill(x + width - 4, foundTop + at, x + width, foundTop + at + bar, Theme.CTRL);
        }
        // MNX-088: a route is a rule for every material; the box shows one real recipe of the pack and says so.
        if (hovered != null) RecipeHover.draw(g, font, hovered, tr("example"), mx, my);
    }

    private int toggle(GuiGraphics g, Font font, int cy, int mx, int my, boolean on, Component label, Component consequence, Runnable action) {
        Ui.toggle(g, x, cy + 1, on);
        g.drawString(font, label, x + 28, cy + 2, Theme.TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(consequence.getString(), width - 28), x + 28, cy + 13, Theme.MUTED, false);
        if (!readOnly) hits.add(x, cy, 32 + font.width(label), 24, action, null, List.of());
        return cy + 28;
    }

    /** {@code − n +} with what is counted next to it. */
    private void stepper(GuiGraphics g, Font font, int sx, int sy, int value, Component unit, int mx, int my, Runnable less, Runnable more) {
        String text = String.valueOf(value);
        if (readOnly) {
            g.drawString(font, text + " × " + unit.getString(), sx, sy + 4, Theme.TEXT, false);
            return;
        }
        button(g, font, Component.literal("−"), sx, sy, mx, my, less);
        g.drawString(font, text, sx + 16 + (14 - font.width(text)) / 2, sy + 4, Theme.TEXT, false);
        button(g, font, Component.literal("+"), sx + 30, sy, mx, my, more);
        g.drawString(font, font.plainSubstrByWidth(unit.getString(), 76), sx + 50, sy + 4, Theme.MUTED, false);
    }

    private void button(GuiGraphics g, Font font, Component label, int bx, int by, int mx, int my, Runnable action) {
        int bw = Math.max(16, font.width(label) + 12);
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        Ui.button(g, font, label, bx, by, bw, 16, false, over, true);
        hits.add(bx, by, bw, 16, action, null, List.of());
    }
}
