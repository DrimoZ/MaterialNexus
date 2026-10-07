package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.network.ProcessPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * "Process" tab of a form view (MNX-036): how this form is made, for every material. Routes come from the
 * existing recipes of the pack (each one copyable by construction); ratios, exclusive and enforce are edited as
 * a pending change, written only through Preview / Apply.
 */
final class ProcessPanel {
    private static final int ROW = 16;
    private static final ProcessRules.Rule EMPTY = new ProcessRules.Rule(List.of(), false, false);

    private final boolean readOnly;
    private final Runnable refresh;
    private ProcessPayload payload;
    private int x, y, width, height;

    ProcessPanel(boolean readOnly, Runnable refresh) {
        this.readOnly = readOnly;
        this.refresh = refresh;
    }

    void accept(ProcessPayload payload) { this.payload = payload; }

    boolean shows(String form) { return payload != null && payload.form().equals(form); }

    void layout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    private ProcessRules.Rule rule() {
        return PendingChanges.process(payload.form()).orElse(payload.rule().orElse(EMPTY));
    }

    private void edit(UnaryOperator<ProcessRules.Rule> change) {
        ProcessRules.Rule next = change.apply(rule());
        if (next.equals(payload.rule().orElse(EMPTY))) PendingChanges.clearProcess(payload.form());
        else PendingChanges.setProcess(payload.form(), next);
        refresh.run();
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
        return new ProcessRules.Route(r.machine(), r.input(), Math.clamp(in, 1, 64), Math.clamp(out, 1, 64));
    }

    private int routesTop() { return y + 14; }

    private int optionsTop() { return routesTop() + Math.max(1, rule().routes().size()) * ROW + 4; }

    private int examplesTop() { return optionsTop() + 26; }

    private List<ProcessRules.Route> addable() {
        return payload.examples().routes().stream().filter(e -> rule().routes().stream()
                .noneMatch(r -> r.machine().equals(e.machine()) && r.input().equals(e.input()))).toList();
    }

    void init(Consumer<AbstractWidget> add) {
        if (payload == null || readOnly) return;
        ProcessRules.Rule rule = rule();
        int bx = x + width - 5 * 16;
        for (int i = 0; i < rule.routes().size(); i++) {
            int index = i;
            int ry = routesTop() + i * ROW;
            add.accept(small("-", bx, ry, () -> editRoute(index, r -> ratio(r, r.in() - 1, r.out()))));
            add.accept(small("+", bx + 16, ry, () -> editRoute(index, r -> ratio(r, r.in() + 1, r.out()))));
            add.accept(small("-", bx + 32, ry, () -> editRoute(index, r -> ratio(r, r.in(), r.out() - 1))));
            add.accept(small("+", bx + 48, ry, () -> editRoute(index, r -> ratio(r, r.in(), r.out() + 1))));
            add.accept(small("×", bx + 64, ry, () -> editRoute(index, null)));
        }
        int oy = optionsTop();
        add.accept(Button.builder(toggle("screen.materialnexus.process.exclusive", rule.exclusive()),
                b -> edit(r -> new ProcessRules.Rule(r.routes(), !r.exclusive(), r.enforceRatio()))).bounds(x, oy, 150, 18).build());
        add.accept(Button.builder(toggle("screen.materialnexus.process.enforce", rule.enforceRatio()),
                b -> edit(r -> new ProcessRules.Rule(r.routes(), r.exclusive(), !r.enforceRatio()))).bounds(x + 154, oy, 150, 18).build());
        if (PendingChanges.process(payload.form()).isPresent()) {
            add.accept(Button.builder(Component.translatable("screen.materialnexus.process.reset"), b -> {
                PendingChanges.clearProcess(payload.form());
                refresh.run();
            }).bounds(x + 308, oy, 70, 18).build());
        }
        List<ProcessRules.Route> examples = addable();
        int ey = examplesTop() + 12;
        // ponytail: no scrolling, examples that do not fit are cut; the server sends the 24 most common.
        for (int i = 0; i < examples.size() && ey + i * ROW + 14 <= y + height; i++) {
            ProcessRules.Route e = examples.get(i);
            add.accept(small("+", x + width - 16, ey + i * ROW, () -> edit(r -> {
                List<ProcessRules.Route> routes = new ArrayList<>(r.routes());
                routes.add(e);
                return new ProcessRules.Rule(routes, r.exclusive(), r.enforceRatio());
            })));
        }
    }

    void render(GuiGraphics g, Font font) {
        if (payload == null) {
            g.drawString(font, Component.translatable("screen.materialnexus.process.loading"), x, y, 0x888888);
            return;
        }
        boolean pending = PendingChanges.process(payload.form()).isPresent();
        g.drawString(font, Component.translatable("screen.materialnexus.process.title", Names.form(payload.form())), x, y, pending ? 0x55FF55 : 0xFFFFFF);
        ProcessRules.Rule rule = rule();
        int textWidth = width - (readOnly ? 0 : 5 * 16 + 4);
        if (rule.routes().isEmpty()) g.drawString(font, Component.translatable("screen.materialnexus.process.none"), x + 6, routesTop() + 4, 0x888888);
        for (int i = 0; i < rule.routes().size(); i++) {
            g.drawString(font, font.plainSubstrByWidth(line(rule.routes().get(i)).getString(), textWidth), x + 6, routesTop() + i * ROW + 4, 0xFFFFFF);
        }
        if (readOnly) {
            g.drawString(font, toggle("screen.materialnexus.process.exclusive", rule.exclusive()), x, optionsTop() + 5, 0xAAAAAA);
            g.drawString(font, toggle("screen.materialnexus.process.enforce", rule.enforceRatio()), x + 154, optionsTop() + 5, 0xAAAAAA);
        }
        List<ProcessRules.Route> examples = addable();
        g.drawString(font, Component.translatable(examples.isEmpty() ? "screen.materialnexus.process.no_examples" : "screen.materialnexus.process.examples"),
                x, examplesTop(), 0xAAAAAA);
        int ey = examplesTop() + 12;
        for (int i = 0; i < examples.size() && ey + i * ROW + 14 <= y + height; i++) {
            g.drawString(font, font.plainSubstrByWidth(line(examples.get(i)).getString(), width - 22), x + 6, ey + i * ROW + 4, 0x88AAFF);
        }
    }

    private Component line(ProcessRules.Route r) {
        return Component.translatable("screen.materialnexus.process.route", r.machine().toString(), r.in(), Names.form(r.input().name()),
                r.out(), Names.form(payload.form()));
    }

    private static Component toggle(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    private static Button small(String label, int x, int y, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, 15, 14).build();
    }
}
