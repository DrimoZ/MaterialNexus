package dev.drimoz.materialnexus.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The player's settings of the Material Nexus screen (MNX-094, MNX-101): client only, one JSON file, nothing to do with
 * the pack. A missing, partial or unreadable file gives the defaults; the file is written when a setting changes.
 */
public final class ScreenSettings {
    public static final String FILE = "screen.json";

    /**
     * {@code showIds}: the technical id under an item's name. {@code openGroups}: the groups that ask nothing ("No
     * duplicate", "Forms that can be created") start open. {@code reopen}: the screen reopens where it was left.
     */
    public record Values(Theme theme, boolean showIds, boolean openGroups, boolean reopen) {
        public static final Values DEFAULT = new Values(Theme.GRAPHITE, true, false, true);

        public Values withTheme(Theme value) { return new Values(value, showIds, openGroups, reopen); }
        public Values withShowIds(boolean value) { return new Values(theme, value, openGroups, reopen); }
        public Values withOpenGroups(boolean value) { return new Values(theme, showIds, value, reopen); }
        public Values withReopen(boolean value) { return new Values(theme, showIds, openGroups, value); }
    }

    private static Values current = Values.DEFAULT;

    private ScreenSettings() { }

    public static Values get() { return current; }

    /** The settings written in {@code json}; anything missing or unreadable keeps its default. */
    public static Values parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Theme theme = Values.DEFAULT.theme();
            if (o.has("theme") && o.get("theme").isJsonPrimitive()) {
                for (Theme t : Theme.values()) if (t.key().equals(o.get("theme").getAsString())) theme = t;
            }
            return new Values(theme, flag(o, "show_ids", Values.DEFAULT.showIds()), flag(o, "open_groups", Values.DEFAULT.openGroups()),
                    flag(o, "reopen", Values.DEFAULT.reopen()));
        } catch (RuntimeException ignored) {
            return Values.DEFAULT;
        }
    }

    private static boolean flag(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    /** The theme named in {@code json}, or the default for anything else. */
    public static Theme parseTheme(String json) { return parse(json).theme(); }

    /** Reads the file of {@code dir} and puts its settings in use. */
    public static void load(Path dir) {
        try {
            Path file = dir.resolve(FILE);
            current = Files.exists(file) ? parse(Files.readString(file)) : Values.DEFAULT;
        } catch (IOException e) {
            current = Values.DEFAULT;
        }
        Theme.use(current.theme());
    }

    /** Puts {@code values} in use and remembers them; a failed write only loses the memory. */
    public static void set(Path dir, Values values) {
        current = values;
        Theme.use(values.theme());
        JsonObject json = new JsonObject();
        json.addProperty("theme", values.theme().key());
        json.addProperty("show_ids", values.showIds());
        json.addProperty("open_groups", values.openGroups());
        json.addProperty("reopen", values.reopen());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE), json + "\n");
        } catch (IOException ignored) {
            // The settings stay in use for this session.
        }
    }

    public static void setTheme(Path dir, Theme theme) { set(dir, current.withTheme(theme)); }
}
