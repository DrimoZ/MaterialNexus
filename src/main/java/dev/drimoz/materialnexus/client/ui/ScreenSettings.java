package dev.drimoz.materialnexus.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The player's settings of the Material Nexus screen (MNX-094): client only, one JSON file, nothing to do with the
 * pack. A missing, partial or unreadable file gives the defaults; the file is written when a setting changes.
 */
public final class ScreenSettings {
    public static final String FILE = "screen.json";

    private ScreenSettings() { }

    /** The theme named in {@code json}, or the default for anything else. */
    public static Theme parseTheme(String json) {
        try {
            String name = JsonParser.parseString(json).getAsJsonObject().get("theme").getAsString();
            for (Theme theme : Theme.values()) if (theme.key().equals(name)) return theme;
        } catch (RuntimeException ignored) {
            // Not our JSON: defaults.
        }
        return Theme.GRAPHITE;
    }

    /** Reads the file of {@code dir} and puts its theme in use. */
    public static void load(Path dir) {
        try {
            Path file = dir.resolve(FILE);
            Theme.use(Files.exists(file) ? parseTheme(Files.readString(file)) : Theme.GRAPHITE);
        } catch (IOException e) {
            Theme.use(Theme.GRAPHITE);
        }
    }

    /** Puts {@code theme} in use and remembers it; a failed write only loses the memory. */
    public static void setTheme(Path dir, Theme theme) {
        Theme.use(theme);
        JsonObject json = new JsonObject();
        json.addProperty("theme", theme.key());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE), json + "\n");
        } catch (IOException ignored) {
            // The theme stays in use for this session.
        }
    }
}
