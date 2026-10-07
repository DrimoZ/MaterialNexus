package dev.drimoz.materialnexus.datapack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Turns GUI choices into a validated diff, then into policy file edits (ADR-008/009). */
public final class PolicyEditor {
    public static final int MAX_CHANGES = 4096;

    /** One line of the preview. {@code from} is empty when the form had no canonical yet. */
    public record Entry(String material, String form, Optional<ResourceLocation> from, ResourceLocation to, boolean valid) { }

    private PolicyEditor() { }

    /**
     * Validates every change against what discovery actually found: a provider that is not a
     * discovered member of that material/form is reported invalid, never written.
     */
    public static List<Entry> preview(ResolvedSnapshot snapshot, List<CanonicalChange> changes) {
        java.util.Set<dev.drimoz.materialnexus.core.domain.MaterialForm> saved = new java.util.HashSet<>();
        snapshot.materials().forEach((material, resolved) -> resolved.forms().forEach((form, f) -> {
            if (f.source() == dev.drimoz.materialnexus.core.policy.PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE || f.ignoredOverride().isPresent()) {
                saved.add(new dev.drimoz.materialnexus.core.domain.MaterialForm(material, form));
            }
        }));
        return preview(snapshot, changes, saved);
    }

    /**
     * {@code saved}: the forms with a choice written in the policy files (MNX-060), the only ones a reset may target;
     * that includes choices the resolver ignores (item no longer in the form, material gone).
     */
    public static List<Entry> preview(ResolvedSnapshot snapshot, List<CanonicalChange> changes,
                                      java.util.Set<dev.drimoz.materialnexus.core.domain.MaterialForm> saved) {
        Map<String, CanonicalChange> byKey = new LinkedHashMap<>();
        for (CanonicalChange change : changes) byKey.put(change.material() + "/" + change.form(), change);

        List<Entry> entries = new ArrayList<>();
        for (CanonicalChange change : byKey.values()) {
            Optional<MaterialId> material = MaterialId.read(change.material()).result();
            Optional<FormId> form = FormId.read(change.form()).result();
            Optional<ResolvedForm> current = material.map(snapshot.materials()::get).map(ResolvedMaterial::forms)
                    .flatMap(forms -> form.map(forms::get));
            // A reset is valid only where a choice is saved; anything else must be a discovered member of that material/form.
            boolean valid = change.reset()
                    ? material.isPresent() && form.isPresent() && saved.contains(new dev.drimoz.materialnexus.core.domain.MaterialForm(material.get(), form.get()))
                    : material.isPresent() && form.isPresent() && snapshot.discovered()
                    .providers(material.get(), form.get()).stream()
                    .anyMatch(p -> p.resource().equals(change.provider()));
            entries.add(new Entry(change.material(), change.form(), current.flatMap(ResolvedForm::canonical), change.provider(), valid));
        }
        entries.sort(Comparator.comparing(Entry::material).thenComparing(Entry::form));
        return entries;
    }

    /**
     * Writes valid entries as {@code preferred_provider} into the file that already declares the
     * material (or a new one), preserving every other field. The previous policy is copied to
     * {@code policies.bak} first, for "Revert last apply".
     */
    public static void apply(Path policiesDir, List<Entry> entries) throws IOException {
        apply(policiesDir, entries, Optional.empty());
    }

    public static void apply(Path policiesDir, List<Entry> entries, Optional<JsonObject> preset) throws IOException {
        apply(policiesDir, entries, preset, Map.of());
    }

    /**
     * Choices, optionally a preset, and process rule edits (all written into global.json, MNX-036), behind a single
     * backup for "Revert last apply". An empty rule removes the form's entry.
     */
    public static void apply(Path policiesDir, List<Entry> entries, Optional<JsonObject> preset,
                             Map<FormId, dev.drimoz.materialnexus.core.policy.ProcessRules.Rule> processes) throws IOException {
        backup(policiesDir);
        if (preset.isPresent() || !processes.isEmpty()) {
            Files.createDirectories(policiesDir);
            Path global = policiesDir.resolve(PolicyFiles.GLOBAL_FILE);
            JsonObject current = Files.exists(global) ? JsonParser.parseString(Files.readString(global, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            if (preset.isPresent()) current = Presets.overlay(current, preset.get());
            if (!processes.isEmpty()) {
                JsonObject rules = child(current, "processes");
                processes.forEach((form, rule) -> {
                    if (rule.routes().isEmpty() && !rule.exclusive() && !rule.enforceRatio()) rules.remove(form.name());
                    else rules.add(form.name(), PolicyFiles.PROCESS.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, rule).getOrThrow());
                });
                if (rules.isEmpty()) current.remove("processes");
            }
            Files.writeString(global, new GsonBuilder().setPrettyPrinting().create().toJson(current), StandardCharsets.UTF_8);
        }

        Path materialsDir = policiesDir.resolve(PolicyFiles.MATERIALS_DIR);
        Files.createDirectories(materialsDir);
        Map<String, Path> fileByMaterial = indexMaterialFiles(materialsDir);

        Map<String, List<Entry>> byMaterial = new LinkedHashMap<>();
        for (Entry e : entries) if (e.valid()) byMaterial.computeIfAbsent(e.material(), m -> new ArrayList<>()).add(e);

        for (var group : byMaterial.entrySet()) {
            boolean onlyResets = group.getValue().stream().allMatch(e -> e.to().equals(CanonicalChange.RESET));
            if (onlyResets && !fileByMaterial.containsKey(group.getKey())) continue;
            Path file = fileByMaterial.computeIfAbsent(group.getKey(), m -> freeFileName(materialsDir, m, fileByMaterial));
            JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            root.addProperty("material", group.getKey());
            JsonObject forms = child(root, "forms");
            for (Entry e : group.getValue()) {
                if (!e.to().equals(CanonicalChange.RESET)) {
                    child(forms, e.form()).addProperty("preferred_provider", e.to().toString());
                } else if (forms.get(e.form()) instanceof JsonObject saved) {
                    // MNX-058: back to default; the form's other fields (priority, process) stay.
                    saved.remove("preferred_provider");
                    if (saved.isEmpty()) forms.remove(e.form());
                }
            }
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        }
    }

    public static Path backupDir(Path policiesDir) {
        return policiesDir.resolveSibling(policiesDir.getFileName() + ".bak");
    }

    public static boolean canRevert(Path policiesDir) {
        return Files.isDirectory(backupDir(policiesDir));
    }

    /**
     * "Revert last apply" (ADR-009): swaps the policy with its backup, so the current policy becomes
     * the new backup and a second revert redoes the apply. One level, no history (ADR-005).
     */
    public static void revert(Path policiesDir) throws IOException {
        Path bak = backupDir(policiesDir);
        if (!Files.isDirectory(bak)) throw new IOException("No previous policy to revert to");
        Path swap = policiesDir.resolveSibling(policiesDir.getFileName() + ".swap");
        deleteRecursively(swap);
        if (Files.exists(policiesDir)) Files.move(policiesDir, swap);
        Files.move(bak, policiesDir);
        if (Files.exists(swap)) Files.move(swap, bak);
        else Files.createDirectories(bak);
    }

    private static void backup(Path policiesDir) throws IOException {
        Path bak = backupDir(policiesDir);
        deleteRecursively(bak);
        Files.createDirectories(bak);
        if (!Files.exists(policiesDir)) return;
        try (Stream<Path> walk = Files.walk(policiesDir)) {
            for (Path source : walk.toList()) {
                Path target = bak.resolve(policiesDir.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target);
            }
        }
    }

    /** Which file declares which material, so an edit never creates a second, conflicting file. */
    private static Map<String, Path> indexMaterialFiles(Path materialsDir) throws IOException {
        Map<String, Path> index = new HashMap<>();
        try (Stream<Path> files = Files.list(materialsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (json.isJsonObject() && json.getAsJsonObject().has("material")) {
                    index.putIfAbsent(json.getAsJsonObject().get("material").getAsString(), file);
                }
            }
        }
        return index;
    }

    private static Path freeFileName(Path dir, String material, Map<String, Path> taken) {
        Path file = dir.resolve(material + ".json");
        for (int i = 2; Files.exists(file) || taken.containsValue(file); i++) file = dir.resolve(material + "_" + i + ".json");
        return file;
    }

    private static JsonObject child(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) parent.add(key, new JsonObject());
        return parent.getAsJsonObject(key);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
