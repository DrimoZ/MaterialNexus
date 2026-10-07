package dev.drimoz.materialnexus.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Presets view (MNX-057): a preset is a ready-made set of global.json settings (mod priority, conversion recipes...).
 * Each one is shown next to the current settings, and "Use" turns it into pending global.json edits: the same
 * Preview and apply as any other change, the per-form choices kept and still winning.
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

    /** True when every field the preset sets already has that value (pending edits included). */
    private static boolean matches(JsonObject preset) {
        return preset.entrySet().stream().allMatch(e -> same(e.getValue(), PendingChanges.global(e.getKey())));
    }

    private static boolean same(JsonElement a, JsonElement b) {
        if (b == null) return a.isJsonArray() && a.getAsJsonArray().isEmpty();
        return a.equals(b);
    }

    private static void use(JsonObject preset) {
        preset.entrySet().forEach(e -> PendingChanges.setGlobal(e.getKey(), e.getValue().deepCopy()));
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        hits.clear();
        g.drawString(font, Component.translatable("screen.materialnexus.presets"), x, y, Ui.TEXT, false);
        int ty = y + 14;
        for (var line : font.split(Component.translatable("screen.materialnexus.presets.intro"), Math.min(w, 560))) {
            g.drawString(font, line, x, ty, Ui.MUTED, false);
            ty += 11;
        }
        ty += 8;
        if (presets.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.presets.none"), x, ty, Ui.FAINT, false);
            return;
        }

        int listW = 170;
        int ly = ty;
        for (var entry : presets.entrySet()) {
            ResourceLocation id = entry.getKey();
            boolean active = id.equals(selected);
            boolean over = mx >= x && mx < x + listW && my >= ly && my < ly + 22;
            g.fill(x, ly, x + listW, ly + 22, active ? (Ui.ACCENT & 0x00FFFFFF) | 0x50000000 : over ? 0x18FFFFFF : 0x0CFFFFFF);
            g.drawString(font, font.plainSubstrByWidth(name(id).getString(), listW - 12), x + 6, ly + 7, active ? Ui.TEXT : Ui.MUTED, false);
            if (matches(entry.getValue())) g.fill(x + listW - 6, ly + 8, x + listW - 2, ly + 14, Ui.SUCCESS);
            hits.add(x, ly, listW, 22, () -> selected = id, null, List.of());
            ly += 24;
        }

        JsonObject preset = presets.get(selected);
        int dx = x + listW + 16;
        int dw = Math.min(w - listW - 16, 420);
        int dy = ty;
        g.drawString(font, name(selected), dx, dy, Ui.TEXT, false);
        dy += 13;
        for (var line : font.split(Component.translatableWithFallback("materialnexus.preset." + selected.getPath() + ".desc", ""), dw)) {
            g.drawString(font, line, dx, dy, Ui.MUTED, false);
            dy += 11;
        }
        dy += 8;
        for (var e : preset.entrySet()) {
            dy = field(g, font, dx, dy, dw, e.getKey(), e.getValue());
            dy += 6;
        }

        dy += 4;
        if (matches(preset)) {
            g.drawString(font, Component.translatable("screen.materialnexus.presets.in_use"), dx, dy + 4, Ui.SUCCESS, false);
        } else {
            Component label = Component.translatable("screen.materialnexus.presets.use");
            int bw = font.width(label) + 20;
            boolean over = mx >= dx && mx < dx + bw && my >= dy && my < dy + 18;
            g.fill(dx, dy, dx + bw, dy + 18, over ? 0xFF6AABF6 : Ui.ACCENT);
            g.drawCenteredString(font, label, dx + bw / 2, dy + 5, Ui.TEXT);
            hits.add(dx, dy, bw, 18, () -> use(preset), null, List.of());
            g.drawString(font, Component.translatable("screen.materialnexus.presets.use_hint"), dx + bw + 8, dy + 5, Ui.FAINT, false);
        }
    }

    /** One setting of the preset: what it is, the preset's value, and the current one. Returns the y below it. */
    private static int field(GuiGraphics g, Font font, int x, int y, int w, String key, JsonElement value) {
        g.fill(x, y, x + 2, y + 9, Ui.ACCENT);
        g.drawString(font, Component.translatableWithFallback("screen.materialnexus.presets.field." + key, key), x + 6, y, Ui.TEXT, false);
        y += 11;
        for (var line : font.split(Component.translatableWithFallback("screen.materialnexus.presets.field." + key + ".desc", ""), w - 6)) {
            g.drawString(font, line, x + 6, y, Ui.FAINT, false);
            y += 10;
        }
        y += 2;
        JsonElement now = PendingChanges.global(key);
        y = valueLine(g, font, x + 6, y, w - 6, Component.translatable("screen.materialnexus.presets.sets"), key, value, Ui.TEXT);
        if (!same(value, now)) y = valueLine(g, font, x + 6, y, w - 6, Component.translatable("screen.materialnexus.presets.now"), key, now, Ui.MUTED);
        return y;
    }

    private static int valueLine(GuiGraphics g, Font font, int x, int y, int w, Component label, String key, JsonElement value, int color) {
        Component text = Component.literal(label.getString() + " ").append(describe(key, value));
        for (var line : font.split(text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** A setting's value in words: mod names in order, form names, else the raw JSON. */
    private static Component describe(String key, JsonElement value) {
        if (value == null || value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
            return Component.translatable("screen.materialnexus.presets.empty");
        }
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
