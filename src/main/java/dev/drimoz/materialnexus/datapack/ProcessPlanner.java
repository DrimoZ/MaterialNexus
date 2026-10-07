package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Process rules (MNX-036) to recipes. A route "machine M: n × input form → m × form" is written by example: a
 * recipe of M that already makes that form from that input for one other material is cloned, every reference to
 * that material is swapped for the target one (tags for tags, items for the canonical item), and the counts are
 * set to the ratio. The clone is then read again; if the counts do not come out right (a shaped pattern, a format
 * keeping counts somewhere unknown) the next example is tried, and with none left the route is reported as
 * unsupported rather than guessed. Pure.
 */
public final class ProcessPlanner {
    public static final String PROCESS = "process_recipe";
    public static final String DISABLE = "process_disable";
    public static final String UNSUPPORTED = "process_unsupported";
    private static final String CONDITIONS = "neoforge:conditions";
    private static final List<String> ID_KEYS = List.of("tag", "item", "id");
    private static final List<String> COUNT_KEYS = List.of("count", "amount");

    private ProcessPlanner() { }

    /**
     * A material reference in a recipe. The id lives in {@code node.field}, or in {@code list[index]} when it is a
     * bare string in a list ({@code field} null). {@code list} is also set for a stack that is a list element. {@code wrapper} is the object holding {@code node} (IE keeps counts there);
     * {@code uses} is how often a shaped-crafting key appears in the pattern.
     */
    private record Ref(MaterialForm target, boolean tag, boolean output, boolean condition,
                       JsonObject node, String field, JsonObject wrapper, JsonArray list, int index, int uses) { }

    private record Reading(Optional<Ref> output, List<Ref> inputs, List<Ref> all) {
        Optional<MaterialId> onlyMaterial() {
            var materials = all.stream().map(r -> r.target().material()).distinct().toList();
            return materials.size() == 1 ? Optional.of(materials.getFirst()) : Optional.empty();
        }

        boolean makes(MaterialForm form) { return output.map(r -> r.target().equals(form)).orElse(false); }

        boolean consumes(MaterialForm form) { return inputs.stream().anyMatch(r -> r.target().equals(form)); }

        List<Ref> inputsOf(MaterialForm form) { return inputs.stream().filter(r -> r.target().equals(form)).toList(); }
    }

    private static final class Index {
        final Map<ResourceLocation, MaterialForm> items = new HashMap<>();
        final Map<ResourceLocation, MaterialForm> tags = new HashMap<>();
        final SortedMap<MaterialId, ResolvedMaterial> resolved;

        Index(SortedMap<MaterialId, ResolvedMaterial> resolved) {
            this.resolved = resolved;
            resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
                MaterialForm key = new MaterialForm(material, form);
                f.canonical().ifPresent(i -> items.put(i, key));
                f.alternatives().forEach(i -> items.put(i, key));
                f.notUnified().forEach(n -> items.putIfAbsent(n.item(), key));
                TagDiscovery.conventionTag(material, form).ifPresent(t -> tags.put(t, key));
            }));
        }

        Optional<ResolvedForm> form(MaterialForm key) {
            return Optional.ofNullable(resolved.get(key.material())).map(m -> m.forms().get(key.form()));
        }
    }

    public static PackContent.Content plan(List<RecipeRewrites.Source> sources, RecipeFormats formats,
                                           SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy) {
        ProcessRules rules = policy.processes();
        if (rules.isEmpty()) return new PackContent.Content(Map.of(), List.of());
        Index index = new Index(resolved);
        Map<String, JsonElement> files = new TreeMap<>();
        List<PackContent.Effect> effects = new ArrayList<>();

        record Known(RecipeRewrites.Source source, List<String> outputKeys, Reading reading) { }
        Map<ResourceLocation, List<Known>> byType = new TreeMap<>();
        for (RecipeRewrites.Source s : sources) {
            if (s.json().isEmpty()) continue;
            RecipeRewrites.format(s.json().get(), formats).ifPresent(format -> byType
                    .computeIfAbsent(ResourceLocation.parse(s.json().get().get("type").getAsString()), t -> new ArrayList<>())
                    .add(new Known(s, format.outputKeys(), read(s.json().get(), format.outputKeys(), index))));
        }

        resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
            MaterialForm target = new MaterialForm(material, form);
            Optional<ProcessRules.Rule> rule = rules.ruleFor(target);
            if (rule.isEmpty() || policy.isExcluded(target) || f.canonical().isEmpty()) return;
            ResourceLocation product = f.canonical().get();

            for (ProcessRules.Route route : rule.get().routes()) {
                MaterialForm input = new MaterialForm(material, route.input());
                if (index.form(input).isEmpty()) continue;
                List<Known> machine = byType.getOrDefault(route.machine(), List.of());
                List<Known> existing = machine.stream().filter(k -> k.reading().makes(target) && k.reading().consumes(input)).toList();
                if (!existing.isEmpty() && (!rule.get().enforceRatio() || existing.stream().anyMatch(k -> hasRatio(k.reading(), input, route)))) continue;
                // The material's own recipe is the best example (it keeps its mold, energy...); then other materials'.
                Optional<JsonObject> recipe = java.util.stream.Stream.concat(existing.stream(),
                                machine.stream().filter(k -> isExample(k.reading(), form, route.input(), material)))
                        .map(k -> copy(k.source().json().get(), k.outputKeys(), material, route, index))
                        .flatMap(Optional::stream)
                        .findFirst();
                if (recipe.isEmpty()) {
                    effects.add(new PackContent.Effect(UNSUPPORTED, route.machine(), product));
                    continue;
                }
                existing.forEach(k -> disable(k.source().id(), product, files, effects));
                ResourceLocation id = recipeId(target, route);
                files.put(path(id), recipe.get());
                effects.add(new PackContent.Effect(PROCESS, id, product));
            }

            if (rule.get().exclusive()) {
                byType.forEach((type, recipes) -> recipes.stream()
                        .filter(k -> k.reading().makes(target))
                        .filter(k -> rule.get().routes().stream().noneMatch(r -> r.machine().equals(type)
                                && k.reading().consumes(new MaterialForm(material, r.input()))))
                        .forEach(k -> disable(k.source().id(), product, files, effects)));
            }
        }));
        return new PackContent.Content(files, effects);
    }

    /**
     * Routes that already exist for {@code form} in some material (machine, input form, ratio), most common first:
     * what the GUI offers to add, each one copyable by construction.
     */
    public static List<ProcessRules.Route> examples(List<RecipeRewrites.Source> sources, RecipeFormats formats,
                                                    SortedMap<MaterialId, ResolvedMaterial> resolved, FormId form) {
        Index index = new Index(resolved);
        Map<ProcessRules.Route, Integer> seen = new HashMap<>();
        for (RecipeRewrites.Source s : sources) {
            if (s.json().isEmpty()) continue;
            var format = RecipeRewrites.format(s.json().get(), formats);
            if (format.isEmpty()) continue;
            Reading r = read(s.json().get(), format.get().outputKeys(), index);
            Optional<MaterialId> material = r.onlyMaterial();
            if (material.isEmpty() || !r.makes(new MaterialForm(material.get(), form))) continue;
            ResourceLocation type = ResourceLocation.parse(s.json().get().get("type").getAsString());
            int out = count(r.output().get());
            r.inputs().stream().map(i -> i.target().form()).distinct().forEach(input -> {
                int in = inputCount(r, new MaterialForm(material.get(), input));
                if (in >= 1 && in <= 64 && out >= 1 && out <= 64) seen.merge(new ProcessRules.Route(type, input, in, out), 1, Integer::sum);
            });
        }
        return seen.entrySet().stream()
                .sorted(Map.Entry.<ProcessRules.Route, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().machine().toString()).thenComparing(e -> e.getKey().input().name()))
                .limit(24).map(Map.Entry::getKey).toList();
    }

    /** {@code materialnexus:process/<form>/<material>/<input>/<in>/<out>/<machine namespace>/<machine path>}. */
    public static ResourceLocation recipeId(MaterialForm target, ProcessRules.Route route) {
        return ResourceLocation.fromNamespaceAndPath("materialnexus", String.join("/", "process", target.form().name(),
                target.material().name(), route.input().name(), String.valueOf(route.in()), String.valueOf(route.out()),
                route.machine().getNamespace(), route.machine().getPath()));
    }

    private static String path(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/recipe/" + id.getPath() + ".json";
    }

    private static void disable(ResourceLocation id, ResourceLocation product, Map<String, JsonElement> files, List<PackContent.Effect> effects) {
        if (files.putIfAbsent(path(id), RecipeRewrites.disabled()) == null) effects.add(new PackContent.Effect(DISABLE, id, product));
    }

    /** Makes {@code form} from {@code input} of one other material only: something to copy. */
    private static boolean isExample(Reading r, FormId form, FormId input, MaterialId target) {
        return r.onlyMaterial().filter(m -> !m.equals(target))
                .map(m -> r.makes(new MaterialForm(m, form)) && r.consumes(new MaterialForm(m, input)))
                .orElse(false);
    }

    private static boolean hasRatio(Reading r, MaterialForm input, ProcessRules.Route route) {
        return inputCount(r, input) == route.in() && r.output().map(ProcessPlanner::count).orElse(-1) == route.out();
    }

    /** The example with the target material swapped in and the route's counts, or empty when it cannot be made exact. */
    private static Optional<JsonObject> copy(JsonObject example, List<String> outputKeys, MaterialId material,
                                             ProcessRules.Route route, Index index) {
        JsonObject recipe = example.deepCopy();
        for (Ref ref : read(recipe, outputKeys, index).all()) {
            MaterialForm swapped = new MaterialForm(material, ref.target().form());
            Optional<ResolvedForm> form = index.form(swapped);
            Optional<ResourceLocation> id = ref.tag()
                    ? form.flatMap(x -> TagDiscovery.conventionTag(material, swapped.form()))
                    : form.flatMap(ResolvedForm::canonical);
            if (id.isEmpty()) return Optional.empty();
            String old = ref.field() == null ? ref.list().get(ref.index()).getAsString() : ref.node().get(ref.field()).getAsString();
            String value = (old.startsWith("#") ? "#" : "") + id.get();
            if (ref.field() == null) ref.list().set(ref.index(), new JsonPrimitive(value));
            else ref.node().addProperty(ref.field(), value);
        }
        MaterialForm input = new MaterialForm(material, route.input());
        Reading swapped = read(recipe, outputKeys, index);
        if (swapped.output().isEmpty() || !setCount(swapped.output().get(), route.out(), true)) return Optional.empty();
        setInput(swapped.inputsOf(input), route.in());
        return hasRatio(read(recipe, outputKeys, index), input, route) ? Optional.of(recipe) : Optional.empty();
    }

    // ---- reading -------------------------------------------------------------------------------------------

    private static Reading read(JsonObject recipe, List<String> outputKeys, Index index) {
        List<Ref> all = new ArrayList<>();
        for (var e : recipe.entrySet()) {
            boolean output = outputKeys.contains(e.getKey());
            boolean condition = e.getKey().equals(CONDITIONS);
            if (e.getKey().equals("key") && e.getValue().isJsonObject() && recipe.get("pattern") instanceof JsonArray pattern) {
                // Shaped crafting: each key ingredient is used once per occurrence of its symbol in the pattern.
                for (var k : e.getValue().getAsJsonObject().entrySet()) {
                    int uses = (int) pattern.asList().stream().mapToLong(row -> row.getAsString().chars().filter(c -> k.getKey().equals(String.valueOf((char) c))).count()).sum();
                    walk(k.getValue(), e.getValue().getAsJsonObject(), k.getKey(), null, -1, false, false, uses, recipe, index, all);
                }
            } else {
                walk(e.getValue(), recipe, e.getKey(), null, -1, output, condition, 1, recipe, index, all);
            }
        }
        List<Ref> inputs = all.stream().filter(r -> !r.output() && !r.condition()).toList();
        return new Reading(all.stream().filter(Ref::output).findFirst(), inputs, all);
    }

    /** {@code element} is {@code owner.ownerKey}, or {@code list[index]} when {@code list} is set. */
    private static void walk(JsonElement element, JsonObject owner, String ownerKey, JsonArray list, int index,
                             boolean output, boolean condition, int uses, JsonObject root, Index idx, List<Ref> refs) {
        if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            JsonObject wrapper = list == null && owner != root ? owner : null;
            for (String key : ID_KEYS) {
                if (o.get(key) instanceof JsonPrimitive p && p.isString()) {
                    ref(p.getAsString(), key.equals("tag"), output, condition, o, key, wrapper, list, index, uses, idx, refs);
                    break;
                }
            }
            for (var e : o.entrySet()) {
                if (!e.getValue().isJsonPrimitive()) walk(e.getValue(), o, e.getKey(), null, -1, output, condition, uses, root, idx, refs);
            }
        } else if (element.isJsonArray()) {
            JsonArray a = element.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                JsonElement child = a.get(i);
                if (child instanceof JsonPrimitive p && p.isString()) {
                    if (output) ref(p.getAsString(), false, true, false, null, null, null, a, i, 1, idx, refs);
                } else {
                    walk(child, owner, ownerKey, a, i, output, condition, uses, root, idx, refs);
                }
            }
        } else if (output && list == null && element instanceof JsonPrimitive p && p.isString()) {
            // A bare id as the whole output: "result": "mod:item".
            ref(p.getAsString(), false, true, false, owner, ownerKey, null, null, -1, 1, idx, refs);
        }
    }

    private static void ref(String value, boolean tagField, boolean output, boolean condition, JsonObject node, String field,
                            JsonObject wrapper, JsonArray list, int index, int uses, Index idx, List<Ref> refs) {
        boolean tag = tagField || value.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(value.startsWith("#") ? value.substring(1) : value);
        MaterialForm target = id == null ? null : tag ? idx.tags.get(id) : idx.items.get(id);
        if (target != null) refs.add(new Ref(target, tag, output, condition, node, field, wrapper, list, index, uses));
    }

    // ---- counts --------------------------------------------------------------------------------------------

    /** Stack size: on the stack itself, or on its wrapper (IE {@code {"basePredicate": {...}, "count": n}}); default 1. */
    private static int count(Ref ref) {
        if (ref.node() != null) for (String k : COUNT_KEYS) if (ref.node().get(k) instanceof JsonPrimitive p && p.isNumber()) return p.getAsInt();
        if (ref.wrapper() != null) for (String k : COUNT_KEYS) if (ref.wrapper().get(k) instanceof JsonPrimitive p && p.isNumber()) return p.getAsInt();
        return 1;
    }

    private static int inputCount(Reading r, MaterialForm input) {
        return r.inputsOf(input).stream().mapToInt(ref -> count(ref) * ref.uses()).sum();
    }

    private static boolean setCount(Ref ref, int n, boolean output) {
        if (count(ref) == n) return true;
        if (ref.node() == null || ref.field() == null) return false;
        for (String k : COUNT_KEYS) if (ref.node().get(k) instanceof JsonPrimitive p && p.isNumber()) { ref.node().addProperty(k, n); return true; }
        if (ref.wrapper() != null) for (String k : COUNT_KEYS) if (ref.wrapper().get(k) instanceof JsonPrimitive p && p.isNumber()) { ref.wrapper().addProperty(k, n); return true; }
        // Only a 1.21 item stack ({"id": ...}) is known to accept a count it did not have.
        if (output && ref.field().equals("id")) { ref.node().addProperty("count", n); return true; }
        return false;
    }

    /** One counted ingredient gets the count; ingredients repeated in a list (Create, shapeless) are repeated n times. */
    private static void setInput(List<Ref> refs, int n) {
        if (refs.isEmpty()) return;
        Ref first = refs.getFirst();
        if (first.node() == null) return;
        boolean counted = first.list() == null || COUNT_KEYS.stream().anyMatch(k -> first.node().has(k));
        if (refs.size() == 1 && counted && first.uses() == 1) {
            setCount(first, n, false);
            return;
        }
        JsonArray list = first.list();
        boolean repeatable = list != null && first.node() != null && refs.stream().allMatch(r -> r.list() == list && r.node() != null && count(r) == 1);
        if (!repeatable) return;
        List<JsonElement> rebuilt = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JsonElement e = list.get(i);
            if (refs.stream().noneMatch(r -> r.node() == e)) rebuilt.add(e);
            else if (e == first.node()) for (int c = 0; c < n; c++) rebuilt.add(e.deepCopy());
        }
        while (!list.isEmpty()) list.remove(0);
        rebuilt.forEach(list::add);
    }
}
