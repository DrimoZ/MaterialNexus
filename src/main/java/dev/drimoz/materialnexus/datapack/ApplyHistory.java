package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A log of what was applied, reverted or restored (MNX-056), one JSON object per line in {@code config/materialnexus/history.jsonl}:
 * {@code {"at": "2026-10-07T14:02:11Z", "kind": "apply", "choices": 3, "rules": 1, "items": 0, "data": 0, "settings": 1,
 * "effects": 42, "snapshot": "1791389531000"}}. Each entry keeps a copy of the policy as it was right after it
 * ({@code config/materialnexus/history/<snapshot>/}, the last {@value #KEPT}), which the Home view can restore (ADR-021).
 */
public final class ApplyHistory {
    public static final String FILE = "history.jsonl";
    public static final String SNAPSHOTS = "history";
    private static final int SHOWN = 12;
    private static final int KEPT = 20;
    private static final Logger LOGGER = LogUtils.getLogger();

    private ApplyHistory() { }

    public static Path file() {
        return MnxPaths.root().resolve(FILE);
    }

    private static Path snapshots() {
        return MnxPaths.root().resolve(SNAPSHOTS);
    }

    public static void append(String kind, int choices, int rules, int items, int data, int settings, int effects) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", kind);
        entry.addProperty("choices", choices);
        entry.addProperty("rules", rules);
        entry.addProperty("items", items);
        entry.addProperty("data", data);
        entry.addProperty("settings", settings);
        entry.addProperty("effects", effects);
        write(entry);
    }

    /** A restore, with the date of the state it brought back. */
    public static void appendRestore(String snapshot) throws IOException {
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", "restore");
        find(snapshot).map(e -> e.get("at")).ifPresent(at -> entry.add("from", at));
        write(entry);
    }

    private static void write(JsonObject entry) throws IOException {
        Instant now = Instant.now();
        entry.addProperty("at", now.toString());
        // The snapshot is a convenience: failing to take it must not fail an apply that is already written.
        String id = String.valueOf(now.toEpochMilli());
        try {
            if (Files.isDirectory(MnxPaths.policies())) {
                Path target = snapshots().resolve(id);
                PolicyEditor.deleteRecursively(target);
                PolicyEditor.copyTree(MnxPaths.policies(), target);
                entry.addProperty("snapshot", id);
                prune();
            }
        } catch (IOException e) {
            LOGGER.warn("Could not keep a copy of the policy for the history", e);
        }
        Files.createDirectories(file().getParent());
        Files.writeString(file(), entry + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Keeps the {@value #KEPT} newest copies. */
    private static void prune() throws IOException {
        try (Stream<Path> dirs = Files.list(snapshots())) {
            List<Path> old = dirs.filter(Files::isDirectory).filter(p -> p.getFileName().toString().matches("\\d+"))
                    .sorted((a, b) -> Long.compare(Long.parseLong(b.getFileName().toString()), Long.parseLong(a.getFileName().toString())))
                    .skip(KEPT).toList();
            for (Path dir : old) PolicyEditor.deleteRecursively(dir);
        }
    }

    /** The saved policy of a history entry, if it is still kept; {@code id} comes from a client and is checked. */
    public static Optional<Path> snapshot(String id) {
        if (!id.matches("\\d{1,19}")) return Optional.empty();
        Path dir = snapshots().resolve(id);
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    private static Optional<JsonObject> find(String snapshot) {
        try {
            if (!Files.isRegularFile(file())) return Optional.empty();
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                try {
                    JsonObject e = JsonParser.parseString(line).getAsJsonObject();
                    if (e.has("snapshot") && e.get("snapshot").getAsString().equals(snapshot)) return Optional.of(e);
                } catch (RuntimeException ignored) {
                    // A hand-edited or truncated line.
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read the history", e);
        }
        return Optional.empty();
    }

    /** The latest entries, newest first, as a JSON array; unreadable lines are skipped, copies no longer kept are dropped. */
    public static String latest() {
        JsonArray out = new JsonArray();
        try {
            if (!Files.isRegularFile(file())) return "[]";
            List<String> lines = Files.readAllLines(file(), StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < SHOWN; i--) {
                try {
                    JsonObject e = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                    if (e.has("snapshot") && snapshot(e.get("snapshot").getAsString()).isEmpty()) e.remove("snapshot");
                    out.add(e);
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
