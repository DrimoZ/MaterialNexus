package dev.drimoz.materialnexus.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.domain.FormId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Process templates (MNX-037, ADR-018) from {@code data/<namespace>/material_nexus/process_templates/*.json}: how to
 * write a recipe of a machine when no recipe of it can be copied, or when a copy cannot carry the ratio.
 * <pre>
 * {"machine": "immersiveengineering:metal_press", "prefer": false,
 *  "forms": {"rod": {"mold": "immersiveengineering:mold_rod"}},      // optional: forms it applies to, with variables
 *  "patterns": {"3": ["#", "#", "#"]},                                 // crafting_shaped: pattern per input count
 *  "recipe": {... "${input}" ... "${in}" ... "${output_item}" ... "${mold}" ...}}
 * </pre>
 * A string that is exactly a placeholder is replaced by a typed value: {@code ${input}} an ingredient object (the
 * input form's tag, else its canonical item), {@code ${inputs}} that ingredient repeated {@code in} times,
 * {@code ${in}}/{@code ${out}} numbers, {@code ${pattern}} the pattern for {@code in}. An object key {@code "${input}"}
 * merges the ingredient's fields into that object (MI and Mekanism stacks). Other placeholders inside strings are
 * replaced as text: {@code ${material}}, {@code ${form}}, {@code ${output_item}}, {@code ${output_tag}}, form variables.
 */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID)
public final class ProcessTemplates extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "material_nexus/process_templates";
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Template(ResourceLocation machine, boolean prefer, Map<FormId, Map<String, String>> forms,
                           Map<String, List<String>> patterns, JsonObject recipe) {
        public boolean appliesTo(FormId form) {
            return forms.isEmpty() || forms.containsKey(form);
        }
    }

    private record Head(ResourceLocation machine, boolean prefer, Map<FormId, Map<String, String>> forms, Map<String, List<String>> patterns) {
        static final Codec<Head> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("machine").forGetter(Head::machine),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.BOOL, "prefer", false).forGetter(Head::prefer),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.unboundedMap(FormId.CODEC, Codec.unboundedMap(Codec.STRING, Codec.STRING)), "forms", Map.of()).forGetter(Head::forms),
                dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()), "patterns", Map.of()).forGetter(Head::patterns)
        ).apply(i, Head::new));
    }

    /** What a template is filled with for one material, form and route. */
    public record Values(String material, String form, JsonObject ingredient, int in, int out, String outputItem, String outputTag) { }

    private static volatile List<Template> templates = List.of();

    private ProcessTemplates() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new ProcessTemplates());
    }

    public static List<Template> templates() {
        return templates;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<Template> loaded = new ArrayList<>();
        new TreeMap<>(files).forEach((file, json) -> parse(json)
                .ifPresentOrElse(loaded::add, () -> LOGGER.warn("Ignoring invalid process template {}", file)));
        templates = List.copyOf(loaded);
    }

    public static Optional<Template> parse(JsonElement json) {
        if (!json.isJsonObject() || !(json.getAsJsonObject().get("recipe") instanceof JsonObject recipe)) return Optional.empty();
        return Head.CODEC.parse(JsonOps.INSTANCE, json).result()
                .map(h -> new Template(h.machine(), h.prefer(), h.forms(), h.patterns(), recipe));
    }

    /** The template filled in, or empty when a placeholder has no value (e.g. no pattern for this count). */
    public static Optional<JsonObject> fill(Template template, Values values) {
        Map<String, String> text = new TreeMap<>(template.forms().getOrDefault(new FormId(values.form()), Map.of()));
        text.put("material", values.material());
        text.put("form", values.form());
        text.put("output_item", values.outputItem());
        text.put("output_tag", values.outputTag());
        try {
            return Optional.of(fill(template.recipe(), template, values, text).getAsJsonObject());
        } catch (MissingValue e) {
            return Optional.empty();
        }
    }

    private static final class MissingValue extends RuntimeException {
        MissingValue() { super(null, null, false, false); }
    }

    private static JsonElement fill(JsonElement element, Template template, Values v, Map<String, String> text) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (var e : element.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("${input}")) v.ingredient().entrySet().forEach(f -> out.add(f.getKey(), f.getValue().deepCopy()));
                else out.add(e.getKey(), fill(e.getValue(), template, v, text));
            }
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(x -> out.add(fill(x, template, v, text)));
            return out;
        }
        if (!(element instanceof JsonPrimitive p) || !p.isString()) return element.deepCopy();
        String s = p.getAsString();
        switch (s) {
            case "${input}" -> { return v.ingredient().deepCopy(); }
            case "${inputs}" -> {
                JsonArray list = new JsonArray();
                for (int i = 0; i < v.in(); i++) list.add(v.ingredient().deepCopy());
                return list;
            }
            case "${in}" -> { return new JsonPrimitive(v.in()); }
            case "${out}" -> { return new JsonPrimitive(v.out()); }
            case "${pattern}" -> {
                List<String> pattern = template.patterns().get(String.valueOf(v.in()));
                if (pattern == null) throw new MissingValue();
                JsonArray rows = new JsonArray();
                pattern.forEach(rows::add);
                return rows;
            }
            default -> { }
        }
        int start = s.indexOf("${");
        while (start >= 0) {
            int end = s.indexOf('}', start);
            if (end < 0) break;
            String value = text.get(s.substring(start + 2, end));
            if (value == null || value.isEmpty()) throw new MissingValue();
            s = s.substring(0, start) + value + s.substring(end + 1);
            start = s.indexOf("${", start + value.length());
        }
        return new JsonPrimitive(s);
    }
}
