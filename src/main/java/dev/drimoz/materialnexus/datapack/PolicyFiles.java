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

    private record FormPolicy(Optional<ResourceLocation> preferredProvider, List<String> modPriority) {
        static final Codec<FormPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("preferred_provider").forGetter(FormPolicy::preferredProvider),
                Codec.STRING.listOf().optionalFieldOf("mod_priority", List.of()).forGetter(FormPolicy::modPriority)
        ).apply(i, FormPolicy::new));
    }

    private record MaterialPolicy(MaterialId material, List<String> modPriority, Map<FormId, FormPolicy> forms) {
        static final Codec<MaterialPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                MaterialId.CODEC.fieldOf("material").forGetter(MaterialPolicy::material),
                Codec.STRING.listOf().optionalFieldOf("mod_priority", List.of()).forGetter(MaterialPolicy::modPriority),
                Codec.unboundedMap(FormId.CODEC, FormPolicy.CODEC).optionalFieldOf("forms", Map.of()).forGetter(MaterialPolicy::forms)
        ).apply(i, MaterialPolicy::new));
    }

    /** Unknown fields are ignored so later sections (e.g. almost_unified) do not break older readers. */
    private record GlobalPolicy(List<String> modPriority, List<String> exclude, List<String> conversionRecipes,
                                Map<String, String> almostUnified) {
        static final Codec<GlobalPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("mod_priority", List.of()).forGetter(GlobalPolicy::modPriority),
                Codec.STRING.listOf().optionalFieldOf("exclude", List.of()).forGetter(GlobalPolicy::exclude),
                Codec.STRING.listOf().optionalFieldOf("conversion_recipes", List.of()).forGetter(GlobalPolicy::conversionRecipes),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("almost_unified", Map.of()).forGetter(GlobalPolicy::almostUnified)
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
        JsonElement global = new JsonObject();
        Path globalFile = policiesDir.resolve(GLOBAL_FILE);
        if (Files.isRegularFile(globalFile)) global = readJson(globalFile);

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
            });
        });
        Set<FormId> conversionForms = new HashSet<>();
        for (String form : globalPolicy.conversionRecipes()) {
            conversionForms.add(FormId.read(form).result().orElseThrow(() -> new IllegalArgumentException(
                    "Invalid policy file " + GLOBAL_FILE + ": bad conversion_recipes entry '" + form + "'")));
        }
        return new ResolutionPolicy(globalPolicy.modPriority(), materialPriority, formPriority, explicit, excludedMaterials, excludedForms,
                conversionForms, almostUnified(globalPolicy.almostUnified()));
    }

    private static <T> T decode(Codec<T> codec, JsonElement json, String file) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException("Invalid policy file " + file + ": " + error));
    }

    private static JsonElement readJson(Path file) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON in policy file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
