package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Reads the pack author's policy (ADR-008): {@code policies/global.json} and
 * {@code policies/materials/*.json}. Any invalid file fails the whole load with the file named,
 * so a typo never silently resolves with half a policy.
 */
public final class PolicyFiles {
    public static final String GLOBAL_FILE = "global.json";
    public static final String MATERIALS_DIR = "materials";

    private static final Codec<ProcessRules.Route> ROUTE = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("machine").forGetter(ProcessRules.Route::machine),
            FormId.CODEC.fieldOf("input").forGetter(ProcessRules.Route::input),
            Codec.intRange(1, 64).fieldOf("in").forGetter(ProcessRules.Route::in),
            Codec.intRange(1, 64).fieldOf("out").forGetter(ProcessRules.Route::out)
    ).apply(i, ProcessRules.Route::new));

    /** {@code {"routes": [...], "exclusive": false, "enforce_ratio": false}}; MNX-036. */
    static final Codec<ProcessRules.Rule> PROCESS = RecordCodecBuilder.create(i -> i.group(
            dev.drimoz.materialnexus.core.OptionalFields.strict(ROUTE.listOf(), "routes", List.of()).forGetter(ProcessRules.Rule::routes),
            dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.BOOL, "exclusive", false).forGetter(ProcessRules.Rule::exclusive),
            dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.BOOL, "enforce_ratio", false).forGetter(ProcessRules.Rule::enforceRatio)
    ).apply(i, ProcessRules.Rule::new));

    private record FormPolicy(Optional<ResourceLocation> preferredProvider, List<String> modPriority, Optional<ProcessRules.Rule> process) {
        static final Codec<FormPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                dev.drimoz.materialnexus.core.OptionalFields.strict(ResourceLocation.CODEC, "preferred_provider").forGetter(FormPolicy::preferredProvider),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "mod_priority", List.of()).forGetter(FormPolicy::modPriority),
                dev.drimoz.materialnexus.core.OptionalFields.strict(PROCESS, "process").forGetter(FormPolicy::process)
        ).apply(i, FormPolicy::new));
    }

    private record MaterialPolicy(MaterialId material, List<String> modPriority, Map<FormId, FormPolicy> forms) {
        static final Codec<MaterialPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                MaterialId.CODEC.fieldOf("material").forGetter(MaterialPolicy::material),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "mod_priority", List.of()).forGetter(MaterialPolicy::modPriority),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.unboundedMap(FormId.CODEC, FormPolicy.CODEC), "forms", Map.of()).forGetter(MaterialPolicy::forms)
        ).apply(i, MaterialPolicy::new));
    }

    /** Unknown fields are ignored so later sections (e.g. almost_unified) do not break older readers. */
    private record GlobalPolicy(List<String> modPriority, List<String> exclude, List<String> conversionRecipes,
                                Map<String, String> almostUnified, Map<FormId, ProcessRules.Rule> processes, List<ResourceLocation> notSame,
                                boolean addMissingTags) {
        static final Codec<GlobalPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "mod_priority", List.of()).forGetter(GlobalPolicy::modPriority),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "exclude", List.of()).forGetter(GlobalPolicy::exclude),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "conversion_recipes", List.of()).forGetter(GlobalPolicy::conversionRecipes),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.unboundedMap(Codec.STRING, Codec.STRING), "almost_unified", Map.of()).forGetter(GlobalPolicy::almostUnified),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.unboundedMap(FormId.CODEC, PROCESS), "processes", Map.of()).forGetter(GlobalPolicy::processes),
                dev.drimoz.materialnexus.core.OptionalFields.strict(ResourceLocation.CODEC.listOf(), "not_same", List.of()).forGetter(GlobalPolicy::notSame),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.BOOL, "add_missing_tags", false).forGetter(GlobalPolicy::addMissingTags)
        ).apply(i, GlobalPolicy::new));
    }

    /** {@code "almost_unified": {"tags": "mnx", "output_rewrite": "au", ...}}; unknown domains or owners fail loudly. */
    private static AlmostUnified almostUnified(Map<String, String> raw) {
        Map<AlmostUnified.Domain, AlmostUnified.Owner> owners = new EnumMap<>(AlmostUnified.Domain.class);
        raw.forEach((domain, owner) -> {
            AlmostUnified.Domain d = Arrays.stream(AlmostUnified.Domain.values()).filter(x -> x.key.equals(domain)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid policy file " + GLOBAL_FILE + ": unknown almost_unified domain '" + domain + "'"));
            AlmostUnified.Owner o = switch (owner) {
                case "mnx" -> AlmostUnified.Owner.MNX;
                case "au" -> AlmostUnified.Owner.AU;
                default -> throw new IllegalArgumentException("Invalid policy file " + GLOBAL_FILE + ": almost_unified." + domain + " must be \"mnx\" or \"au\"");
            };
            owners.put(d, o);
        });
        return new AlmostUnified(owners);
    }

    private PolicyFiles() { }

    public static ResolutionPolicy load(Path policiesDir) throws IOException {
        return load(policiesDir, java.util.Optional.empty());
    }

    /** The policy as written, or with a preset applied on top of global.json (MNX-019 preview). */
    public static ResolutionPolicy load(Path policiesDir, java.util.Optional<JsonObject> preset) throws IOException {
        JsonElement global = new JsonObject();
        Path globalFile = policiesDir.resolve(GLOBAL_FILE);
        if (Files.isRegularFile(globalFile)) global = readJson(globalFile);
        if (preset.isPresent()) global = Presets.overlay(global.isJsonObject() ? global.getAsJsonObject() : new JsonObject(), preset.get());

        Map<String, JsonElement> materials = new TreeMap<>();
        Path materialsDir = policiesDir.resolve(MATERIALS_DIR);
        if (Files.isDirectory(materialsDir)) {
            try (Stream<Path> files = Files.list(materialsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                    materials.put(file.getFileName().toString(), readJson(file));
                }
            }
        }
        return parse(global, materials);
    }

    /** Pure part of {@link #load}: file name to JSON in, policy out. */
    public static ResolutionPolicy parse(JsonElement global, Map<String, JsonElement> materialsByFile) {
        GlobalPolicy globalPolicy = decode(GlobalPolicy.CODEC, global, GLOBAL_FILE);
        Set<MaterialId> excludedMaterials = new HashSet<>();
        Set<MaterialForm> excludedForms = new HashSet<>();
        for (String entry : globalPolicy.exclude()) {
            // "steel" excludes a whole material, "steel/rod" one of its forms.
            String[] parts = entry.split("/", -1);
            var material = MaterialId.read(parts[0]).result();
            var form = parts.length == 2 ? FormId.read(parts[1]).result() : Optional.<FormId>empty();
            if (material.isEmpty() || parts.length > 2 || (parts.length == 2 && form.isEmpty())) {
                throw new IllegalArgumentException("Invalid policy file " + GLOBAL_FILE + ": bad exclude entry '" + entry + "'");
            }
            if (form.isPresent()) excludedForms.add(new MaterialForm(material.get(), form.get()));
            else excludedMaterials.add(material.get());
        }
        Map<MaterialId, String> seen = new HashMap<>();
        Map<MaterialId, List<String>> materialPriority = new HashMap<>();
        Map<MaterialForm, List<String>> formPriority = new HashMap<>();
        Map<MaterialForm, ResourceLocation> explicit = new HashMap<>();
        Map<MaterialForm, ProcessRules.Rule> processOverrides = new HashMap<>();

        materialsByFile.forEach((file, json) -> {
            MaterialPolicy m = decode(MaterialPolicy.CODEC, json, file);
            String previous = seen.putIfAbsent(m.material(), file);
            if (previous != null) {
                throw new IllegalArgumentException("Material '" + m.material() + "' is defined in both " + previous + " and " + file);
            }
            if (!m.modPriority().isEmpty()) materialPriority.put(m.material(), m.modPriority());
            m.forms().forEach((form, f) -> {
                MaterialForm key = new MaterialForm(m.material(), form);
                if (!f.modPriority().isEmpty()) formPriority.put(key, f.modPriority());
                f.preferredProvider().ifPresent(item -> explicit.put(key, item));
                f.process().ifPresent(rule -> processOverrides.put(key, rule));
            });
        });
        Set<FormId> conversionForms = new HashSet<>();
        for (String form : globalPolicy.conversionRecipes()) {
            conversionForms.add(FormId.read(form).result().orElseThrow(() -> new IllegalArgumentException(
                    "Invalid policy file " + GLOBAL_FILE + ": bad conversion_recipes entry '" + form + "'")));
        }
        return new ResolutionPolicy(globalPolicy.modPriority(), materialPriority, formPriority, explicit, excludedMaterials, excludedForms,
                conversionForms, almostUnified(globalPolicy.almostUnified()), new ProcessRules(globalPolicy.processes(), processOverrides),
                new HashSet<>(globalPolicy.notSame()), globalPolicy.addMissingTags());
    }

    private static <T> T decode(Codec<T> codec, JsonElement json, String file) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {
            throw new IllegalArgumentException("Invalid policy file " + file + ": " + error);
        });
    }

    private static JsonElement readJson(Path file) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON in policy file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
