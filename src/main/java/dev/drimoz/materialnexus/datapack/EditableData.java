package dev.drimoz.materialnexus.datapack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.drimoz.materialnexus.core.domain.MaterialDefinition;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Material Nexus data editable in game (MNX-046): forms, process templates, recipe formats, presets and material
 * definitions. Edits are files of a datapack in {@code config/materialnexus/policies/data/}, loaded above every other:
 * an edit with the id of a shipped file replaces it, a new id adds one, deleting the edit restores the original. Being
 * under {@code policies/}, edits are covered by "Revert last apply" and ship with the modpack like the policy.
 */
public final class EditableData {
    public static final String USER_PACK_ID = "materialnexus_user";
    public static final int MAX_TEXT = 65536;
    public static final int MAX_EDITS = 64;

    public enum Kind {
        FORMS("forms", FormPatterns.DIRECTORY),
        PROCESS_TEMPLATES("process_templates", ProcessTemplates.DIRECTORY),
        RECIPE_FORMATS("recipe_formats", RecipeFormats.DIRECTORY),
        PRESETS("presets", Presets.DIRECTORY),
        MATERIALS("materials", MaterialDefinitions.DIRECTORY);

        public final String key;
        public final String directory;

        Kind(String key, String directory) {
            this.key = key;
            this.directory = directory;
        }

        public static Optional<Kind> byKey(String key) {
            return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
        }
    }

    /** One data file as the game sees it: who provides the version in use, and whether it is an in-game edit. */
    public record Entry(Kind kind, ResourceLocation id, String source, boolean edited) { }

    private EditableData() { }

    public static Path userPack() {
        return MnxPaths.policies().resolve("data");
    }

    public static List<Entry> list(ResourceManager resources) {
        List<Entry> entries = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            resources.listResourceStacks(kind.directory, f -> f.getPath().endsWith(".json")).forEach((file, stack) -> {
                if (stack.isEmpty()) return;
                String source = stack.get(stack.size() - 1).sourcePackId();
                entries.add(new Entry(kind, id(kind, file), source, source.equals(USER_PACK_ID)));
            });
        }
        entries.sort(java.util.Comparator.comparing((Entry e) -> e.kind().ordinal()).thenComparing(e -> e.id().toString()));
        return entries;
    }

    /** The version in use, pretty-printed; empty if there is none. */
    public static Optional<String> read(ResourceManager resources, Kind kind, ResourceLocation id) {
        List<Resource> stack = resources.getResourceStack(file(kind, id));
        if (stack.isEmpty()) return Optional.empty();
        try (Reader reader = stack.get(stack.size() - 1).openAsReader()) {
            return Optional.of(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseReader(reader)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Why this text cannot be used, or empty if it can. Read with the same codecs as the files; a process template is
     * also filled for iron and must decode as a recipe ({@code decodes}), like the GameTest does for shipped ones.
     */
    public static Optional<String> validate(Kind kind, String text, Predicate<JsonObject> decodes) {
        JsonElement json;
        try {
            json = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            return Optional.of("invalid JSON: " + e.getMessage());
        }
        if (!json.isJsonObject()) return Optional.of("a JSON object is expected");
        return switch (kind) {
            case FORMS -> FormPatterns.validate(json);
            case RECIPE_FORMATS -> RecipeFormats.validate(json);
            case MATERIALS -> MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().map(e -> e.message());
            case PRESETS -> {
                try {
                    PolicyFiles.parse(Presets.overlay(new JsonObject(), json.getAsJsonObject()), Map.of());
                    yield Optional.empty();
                } catch (RuntimeException e) {
                    yield Optional.of(e.getMessage());
                }
            }
            case PROCESS_TEMPLATES -> {
                var template = ProcessTemplates.parse(json);
                if (template.isEmpty()) yield Optional.of("needs \"machine\" and a \"recipe\" object");
                JsonObject ingredient = new JsonObject();
                ingredient.addProperty("tag", "forge:ingots/iron");
                String form = template.get().forms().isEmpty() ? "plate" : template.get().forms().keySet().iterator().next().name();
                int in = template.get().patterns().keySet().stream().mapToInt(Integer::parseInt).min().orElse(1);
                var filled = ProcessTemplates.fill(template.get(), new ProcessTemplates.Values("iron", form, ingredient, in, 1,
                        "minecraft:iron_ingot", "forge:ingots/iron"));
                if (filled.isEmpty()) yield Optional.of("a placeholder has no value");
                yield decodes.test(filled.get()) ? Optional.<String>empty() : Optional.of("the game cannot read the filled recipe");
            }
        };
    }

    /** Writes ({@code text} present) or removes (empty: back to the original) in-game edits. */
    public static void write(Map<Map.Entry<Kind, ResourceLocation>, Optional<String>> edits) throws IOException {
        Path pack = userPack();
        ensurePack(pack);
        for (var e : edits.entrySet()) {
            Path file = pack.resolve("data").resolve(e.getKey().getValue().getNamespace())
                    .resolve(e.getKey().getKey().directory).resolve(e.getKey().getValue().getPath() + ".json").normalize();
            if (!file.startsWith(pack)) throw new IOException("Edit path escapes the pack: " + e.getKey().getValue());
            if (e.getValue().isPresent()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(e.getValue().get())),
                        StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(file);
            }
        }
    }

    public static void ensurePack(Path pack) throws IOException {
        Files.createDirectories(pack);
        Path meta = pack.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\"pack\": {\"description\": \"Material Nexus in-game edits\", \"pack_format\": "
                    + net.minecraft.SharedConstants.getCurrentVersion().getPackVersion(net.minecraft.server.packs.PackType.SERVER_DATA) + "}}",
                    StandardCharsets.UTF_8);
        }
    }

    private static ResourceLocation id(Kind kind, ResourceLocation file) {
        String path = file.getPath().substring(kind.directory.length() + 1);
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(0, path.length() - ".json".length()));
    }

    private static ResourceLocation file(Kind kind, ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), kind.directory + "/" + id.getPath() + ".json");
    }
}
