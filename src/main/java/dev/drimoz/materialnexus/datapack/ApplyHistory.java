package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * A log of what was applied or reverted (MNX-056), one JSON object per line in {@code config/materialnexus/history.jsonl}:
 * {@code {"at": "2026-10-07T14:02:11Z", "kind": "apply", "choices": 3, "rules": 1, "items": 0, "data": 0, "settings": 1,
 * "effects": 42}}. Read-only history shown on the Home view; "Revert last apply" is still one level (ADR-005).
 */
public final class ApplyHistory {
    public static final String FILE = "history.jsonl";
    private static final int SHOWN = 12;

    private ApplyHistory() { }

    public static Path file() {
        return MnxPaths.root().resolve(FILE);
    }

    public static void append(String kind, int choices, int rules, int items, int data, int settings, int effects) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("at", Instant.now().toString());
        entry.addProperty("kind", kind);
        entry.addProperty("choices", choices);
        entry.addProperty("rules", rules);
        entry.addProperty("items", items);
        entry.addProperty("data", data);
        entry.addProperty("settings", settings);
        entry.addProperty("effects", effects);
        Files.createDirectories(file().getParent());
        Files.writeString(file(), entry + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** The latest entries, newest first, as a JSON array; unreadable lines are skipped. */
    public static String latest() {
        JsonArray out = new JsonArray();
        try {
            if (!Files.isRegularFile(file())) return "[]";
            List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < SHOWN; i--) {
                try {
                    out.add(JsonParser.parseString(lines.get(i)).getAsJsonObject());
                } catch (RuntimeException ignored) {
                    // A hand-edited or truncated line: not worth failing the screen for.
                }
            }
        } catch (IOException e) {
            return "[]";
        }
        return out.toString();
    }
}
