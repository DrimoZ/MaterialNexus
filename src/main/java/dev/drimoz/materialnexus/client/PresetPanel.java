package dev.drimoz.materialnexus.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Presets view (MNX-057, MNX-099): a preset is a ready-made set of global.json settings (mod priority, conversion
 * recipes...). The selected one is shown against the current settings, value by value, and "Use" turns it into pending
 * global.json edits: the same Review and apply as any other change, the per-form choices kept and still winning.
 */
final class PresetPanel {
    private final Map<ResourceLocation, JsonObject> presets = new TreeMap<>();
    private final Ui.Hits hits = new Ui.Hits();
    private ResourceLocation selected;

    PresetPanel(Map<ResourceLocation, String> raw) {
        raw.forEach((id, text) -> {
            try {
                presets.put(id, JsonParser.parseString(text).getAsJsonObject());
            } catch (RuntimeException ignored) {
                // An unreadable preset is simply not listed; the server already logged it.
            }
        });
        selected = presets.isEmpty() ? null : presets.keySet().iterator().next();
    }

    boolean click(double mx, double my, int button) {
        return hits.click(mx, my, button);
    }

    private static Component name(ResourceLocation id) {
        return Component.translatableWithFallback("materialnexus.preset." + id.getPath(), id.getPath());
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.materialnexus.presets." + key, args);
    }

    /** True when every field the preset sets already has that value (pending edits included). */
    private static boolean matches(JsonObject preset) {
        return preset.entrySet().stream().allMatch(e -> same(e.getValue(), PendingChanges.global(e.getKey())));
    }

    private static boolean same(JsonElement a, JsonElement b) {
        if (b == null) return a.isJsonArray() && a.getAsJsonArray().isEmpty();
        return a.equals(b);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        if (presets.isEmpty()) {
            g.drawString(font, tr("none"), x, y, Theme.MUTED, false);
            return;
        }
        // The presets, each with whether it is what the pack uses now.
        int listWidth = Math.clamp(w / 4, 150, 216);
        int ly = y;
        for (var entry : presets.entrySet()) {
            ResourceLocation id = entry.getKey();
            boolean active = id.equals(selected);
            boolean over = mx >= x && mx < x + listWidth && my >= ly && my < ly + 22;
            if (active) g.fill(x, ly, x + listWidth, ly + 22, Theme.SEL);
            else if (over) g.fill(x, ly, x + listWidth, ly + 22, Theme.RAISED);
            boolean inUse = matches(entry.getValue());
            if (inUse) Ui.mark(g, Ui.Status.UNIFIED, x + 6, ly + 8);
            g.drawString(font, font.plainSubstrByWidth(name(id).getString(), listWidth - 24), x + 18, ly + 7, active || over ? Theme.TEXT : Theme.MUTED, false);
            hits.add(x, ly, listWidth, 22, () -> selected = id, null, List.of());
            ly += 24;
        }
        g.fill(x + listWidth + 11, y - 10, x + listWidth + 12, y + h + 10, Theme.BORDER);

        JsonObject preset = presets.get(selected);
        int dx = x + listWidth + 24;
        int dw = Math.min(w - listWidth - 24, 760);
        int dy = y;
        g.drawString(font, name(selected).copy().withStyle(ChatFormatting.BOLD), dx, dy, Theme.TEXT, false);
        dy += 12;
        for (var line : font.split(Component.translatableWithFallback("materialnexus.preset." + selected.getPath() + ".desc", ""), dw)) {
            g.drawString(font, line, dx, dy, Theme.MUTED, false);
            dy += 10;
        }
        dy += 6;
        var intro = font.split(tr("intro"), dw - 16);
        g.fill(dx, dy, dx + dw, dy + intro.size() * 10 + 8, Theme.RAISED);
        for (int i = 0; i < intro.size(); i++) g.drawString(font, intro.get(i), dx + 8, dy + 4 + i * 10, Theme.TEXT, false);
        dy += intro.size() * 10 + 16;

        // Value by value: what the pack has now, what the preset would put.
        int c1 = Math.clamp(dw / 3, 130, 220);
        int cw = (dw - c1 - 16) / 2;
        g.drawString(font, tr("col.setting"), dx, dy, Theme.MUTED, false);
        g.drawString(font, tr("col.now"), dx + c1, dy, Theme.MUTED, false);
        g.drawString(font, tr("col.preset"), dx + c1 + cw + 16, dy, Theme.MUTED, false);
        dy += 14;
        int changed = 0;
        for (var e : preset.entrySet()) {
            String key = e.getKey();
            JsonElement now = PendingChanges.global(key);
            boolean differs = !same(e.getValue(), now);
            if (differs) changed++;
            var title = font.split(Component.translatableWithFallback("screen.materialnexus.presets.field." + key, key), c1 - 8);
            var help = font.split(Component.translatableWithFallback("screen.materialnexus.presets.field." + key + ".desc", ""), c1 - 8);
            var current = font.split(describe(key, now), cw);
            var next = font.split(differs ? describe(key, e.getValue()) : tr("unchanged"), cw - 12);
            int rows = Math.max(title.size() + help.size(), Math.max(current.size(), next.size()));
            g.fill(dx, dy, dx + dw, dy + rows * 10 + 8, Theme.SURFACE);
            int ty = dy + 4;
            for (var line : title) { g.drawString(font, line, dx + 6, ty, Theme.TEXT, false); ty += 10; }
            for (var line : help) { g.drawString(font, line, dx + 6, ty, Theme.FAINT, false); ty += 10; }
            for (int i = 0; i < current.size(); i++) g.drawString(font, current.get(i), dx + c1, dy + 4 + i * 10, Theme.MUTED, false);
            if (differs) g.renderOutline(dx + c1 + cw + 16, dy + 5, 6, 6, Theme.INFO);
            for (int i = 0; i < next.size(); i++) g.drawString(font, next.get(i), dx + c1 + cw + 28, dy + 4 + i * 10, differs ? Theme.TEXT : Theme.FAINT, false);
            dy += rows * 10 + 10;
        }
        dy += 6;
        if (matches(preset)) {
            Ui.mark(g, Ui.Status.UNIFIED, dx, dy + 5);
            g.drawString(font, tr("in_use"), dx + 12, dy + 4, Theme.OK, false);
        } else {
            Component label = tr("use");
            int bw = font.width(label) + 16;
            boolean over = mx >= dx && mx < dx + bw && my >= dy && my < dy + 18;
            Ui.button(g, font, label, dx, dy, bw, 18, false, over, true);
            hits.add(dx, dy, bw, 18, () -> preset.entrySet().forEach(e -> PendingChanges.setGlobal(e.getKey(), e.getValue().deepCopy())), null, List.of());
            g.drawString(font, font.plainSubstrByWidth(tr("use_effect", changed).getString(), dw - bw - 12), dx + bw + 8, dy + 5, Theme.MUTED, false);
        }
    }

    /** A setting's value in words: mod names in order, form names, else the raw JSON. */
    private static Component describe(String key, JsonElement value) {
        if (value == null || value.isJsonArray() && value.getAsJsonArray().isEmpty()) return tr("empty");
        if (value instanceof JsonArray array && array.asList().stream().allMatch(JsonElement::isJsonPrimitive)) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                String s = array.get(i).getAsString();
                parts.add(switch (key) {
                    case "mod_priority" -> (i + 1) + ". " + NexusScreen.modName(s);
                    case "conversion_recipes" -> Names.form(s).getString();
                    default -> s;
                });
            }
            return Component.literal(String.join(key.equals("mod_priority") ? "  " : ", ", parts));
        }
        String raw = value.toString();
        return Component.literal(raw.length() > 200 ? raw.substring(0, 200) + "…" : raw);
    }
}
