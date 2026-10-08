package dev.drimoz.materialnexus.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.AlmostUnified;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the generated pack contains for a resolved policy (ADR-007). Only forms unified by a
 * decision of the player (any policy level, never the default) produce anything: alternatives
 * are removed from the material tag, and listed forms get a 1:1 conversion recipe.
 */
public final class PackContent {
    public static final String TAG_REMOVE = "tag_remove";
    /** MNX-078: an item known as a form gets the convention tag it lacks ({@code target} the tag). */
    public static final String TAG_ADD = "tag_add";
    public static final String CONVERSION = "conversion_recipe";
    /** Alternatives the server swaps for the canonical item when the game touches them (MNX-028). */
    public static final String ITEM_CONVERSION = "item_conversion";
    /** Preview only: an item created for a missing form (MNX-039), {@code target} the item, {@code item} the tag it joins. */
    public static final String ITEM_CREATE = "item_create";
    /** A generated or rewritten recipe the game could not decode (MNX-037): not written, the original recipe stays. */
    public static final String RECIPE_INVALID = "recipe_invalid";
    /** Informational: a domain left to Almost Unified ({@code target} = materialnexus:&lt;domain&gt;). Generates no file. */
    public static final String ALMOST_UNIFIED = "almost_unified";
    /** Preview only, never written (MNX-076): target, the item scripts keep; item, the one Material Nexus would keep. */
    public static final String SCRIPT_CONFLICT = "script_conflict";
    private static final ResourceLocation AU_ID = ResourceLocation.fromNamespaceAndPath(AlmostUnified.MOD_ID, "owner");

    /** One generated change; for both conversions, {@code target} is the canonical item. */
    public record Effect(String kind, ResourceLocation target, ResourceLocation item) { }

    public record Content(Map<String, JsonElement> files, List<Effect> effects) { }

    private static final Comparator<Effect> ORDER = Comparator.comparing(Effect::kind)
            .thenComparing(Effect::target).thenComparing(Effect::item);

    private PackContent() { }

    /** Alternative to canonical item, from the applied pack only: nothing converts before an apply. */
    public static Map<ResourceLocation, ResourceLocation> itemConversions(List<Effect> effects) {
        Map<ResourceLocation, ResourceLocation> conversions = new TreeMap<>();
        for (Effect e : effects) if (e.kind().equals(ITEM_CONVERSION)) conversions.put(e.item(), e.target());
        return conversions;
    }

    /** Recipe side of {@link #full}: given the conversions and what Material Nexus may do, the recipe content. */
    public interface RecipePlanner {
        Content plan(Map<ResourceLocation, ResourceLocation> conversions, AlmostUnified.Ownership ownership);
    }

    /**
     * Tag/item content plus the recipe content it implies; the generated pack is exactly this. With Almost
     * Unified installed, domains left to it generate nothing and are noted (ADR-012).
     */
    public static Content full(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, boolean auPresent, RecipePlanner recipes) {
        return full(resolved, policy, auPresent, recipes, Map.of());
    }

    /** {@code conventionTags}: see {@link #conventionMembers}, for the tags added under {@code add_missing_tags}. */
    public static Content full(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, boolean auPresent, RecipePlanner recipes,
                               Map<ResourceLocation, Set<ResourceLocation>> conventionTags) {
        AlmostUnified.Ownership ownership = policy.almostUnified().ownership(auPresent);
        Content base = generate(resolved, policy, ownership, conventionTags);
        List<Effect> effects = new ArrayList<>(base.effects());
        Map<String, JsonElement> files = new TreeMap<>(base.files());
        if (auPresent) {
            for (AlmostUnified.Domain d : AlmostUnified.Domain.values()) {
                if (!policy.almostUnified().mnxOwns(d, true)) effects.add(new Effect(ALMOST_UNIFIED, ResourceLocation.fromNamespaceAndPath("materialnexus", d.key), AU_ID));
            }
        }
        Map<ResourceLocation, ResourceLocation> conversions = itemConversions(base.effects());
        if (!conversions.isEmpty()) {
            Content recipe = recipes.plan(conversions, ownership);
            files.putAll(recipe.files());
            effects.addAll(recipe.effects());
        }
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /**
     * Process recipes (MNX-036) on top of the unification content. A recipe the rules disable is disabled even if
     * unification rewrote it: the explicit process decision is the more specific one.
     */
    public static Content withProcesses(Content base, Content processes) {
        Map<String, JsonElement> files = new TreeMap<>(base.files());
        files.putAll(processes.files());
        java.util.Set<ResourceLocation> disabled = new java.util.HashSet<>();
        processes.effects().stream().filter(e -> e.kind().equals(ProcessPlanner.DISABLE)).forEach(e -> disabled.add(e.target()));
        List<Effect> effects = new ArrayList<>(base.effects().stream()
                .filter(e -> !(disabled.contains(e.target()) && (e.kind().equals(RecipeRewrites.REWRITE) || e.kind().equals(RecipeRewrites.DISABLE))))
                .toList());
        effects.addAll(processes.effects());
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /**
     * Every recipe file this pack would write is decoded by the game first ({@code decodes}); one that fails is dropped
     * and reported, so a wrong template or format never reaches the world. Disabling stubs have no type and pass.
     */
    public static Content withValidRecipes(Content content, java.util.function.Predicate<JsonObject> decodes) {
        Map<String, JsonElement> files = new TreeMap<>(content.files());
        java.util.Set<ResourceLocation> invalid = new java.util.HashSet<>();
        for (var e : content.files().entrySet()) {
            String[] parts = e.getKey().split("/", 4);
            if (parts.length < 4 || !parts[0].equals("data") || !parts[2].equals("recipes") || !(e.getValue() instanceof JsonObject json) || !json.has("type")) continue;
            JsonObject recipe = json.deepCopy();
            recipe.remove("conditions");
            if (decodes.test(recipe)) continue;
            files.remove(e.getKey());
            invalid.add(ResourceLocation.fromNamespaceAndPath(parts[1], parts[3].substring(0, parts[3].length() - ".json".length())));
        }
        if (invalid.isEmpty()) return content;
        List<Effect> effects = new ArrayList<>();
        for (Effect e : content.effects()) {
            boolean dropped = invalid.contains(e.target()) && (e.kind().equals(RecipeRewrites.REWRITE) || e.kind().equals(ProcessPlanner.PROCESS));
            effects.add(dropped ? new Effect(RECIPE_INVALID, e.target(), e.item()) : e);
        }
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    public static boolean isUnified(ResolvedForm form) {
        return form.canonical().isPresent() && form.source() != PolicyPrecedence.DEFAULT && !form.alternatives().isEmpty();
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy) {
        return generate(resolved, policy, AlmostUnified.Ownership.ALL);
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, AlmostUnified.Ownership ownership) {
        return generate(resolved, policy, ownership, Map.of());
    }

    public static Content generate(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy, AlmostUnified.Ownership ownership,
                                   Map<ResourceLocation, Set<ResourceLocation>> conventionTags) {
        SortedMap<ResourceLocation, TreeSet<ResourceLocation>> removals = new TreeMap<>();
        SortedMap<ResourceLocation, TreeSet<ResourceLocation>> additions = new TreeMap<>();
        if (policy.addMissingTags() && ownership.tags()) missingTags(resolved, policy, conventionTags, additions);
        Map<String, JsonElement> files = new TreeMap<>();
        List<Effect> effects = new ArrayList<>();
        resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
            if (!isUnified(f)) return;
            ResourceLocation canonical = f.canonical().orElseThrow();
            for (ResourceLocation alt : f.alternatives()) effects.add(new Effect(ITEM_CONVERSION, canonical, alt));
            if (ownership.tags()) {
                TagDiscovery.conventionTag(material, form)
                        .ifPresent(tag -> removals.computeIfAbsent(tag, t -> new TreeSet<>()).addAll(f.alternatives()));
            }
            if (policy.conversionRecipeForms().contains(form)) {
                for (ResourceLocation alt : f.alternatives()) {
                    String id = "convert/" + material.name() + "/" + form.name() + "/" + alt.getNamespace() + "_" + alt.getPath().replace('/', '_');
                    files.put("data/materialnexus/recipes/" + id + ".json", conversionRecipe(alt, canonical));
                    effects.add(new Effect(CONVERSION, canonical, alt));
                }
            }
        }));
        // MNX-079: the player's own edits come last and win over the generated ones.
        if (ownership.tags()) policy.tagEdits().forEach((key, edit) -> TagDiscovery.conventionTag(key.material(), key.form()).ifPresent(tag -> {
            additions.computeIfAbsent(tag, t -> new TreeSet<>()).addAll(edit.add());
            removals.computeIfAbsent(tag, t -> new TreeSet<>()).addAll(edit.remove());
            additions.get(tag).removeAll(edit.remove());
            removals.get(tag).removeAll(edit.add());
        }));
        additions.values().removeIf(java.util.Collection::isEmpty);
        removals.values().removeIf(java.util.Collection::isEmpty);
        Set<ResourceLocation> tags = new TreeSet<>(removals.keySet());
        tags.addAll(additions.keySet());
        for (ResourceLocation tag : tags) {
            Collection<ResourceLocation> removed = removals.getOrDefault(tag, new TreeSet<>());
            Collection<ResourceLocation> added = additions.getOrDefault(tag, new TreeSet<>());
            files.put("data/" + tag.getNamespace() + "/tags/items/" + tag.getPath() + ".json", tagFile(added, removed));
            removed.forEach(item -> effects.add(new Effect(TAG_REMOVE, tag, item)));
            added.forEach(item -> effects.add(new Effect(TAG_ADD, tag, item)));
        }
        effects.sort(ORDER);
        return new Content(files, List.copyOf(effects));
    }

    /**
     * MNX-078 (ADR-023): the items of each form that lack its convention tag, and its folder tag when the pack has one
     * (c:ingots/tin, c:ingots). Only where the pack already uses that convention (the tag or its folder tag exists),
     * only for the items unification keeps (the canonical one; every duplicate when the form is not unified): never a
     * variant or an excluded form, never an alternative that is about to leave the tag.
     */
    public static void missingTags(SortedMap<MaterialId, ResolvedMaterial> resolved, ResolutionPolicy policy,
                                    Map<ResourceLocation, Set<ResourceLocation>> members, SortedMap<ResourceLocation, TreeSet<ResourceLocation>> out) {
        resolved.forEach((material, rm) -> rm.forms().forEach((form, f) -> {
            if (f.canonical().isEmpty() || policy.isExcluded(new dev.drimoz.materialnexus.core.domain.MaterialForm(material, form))) return;
            var tag = TagDiscovery.conventionTag(material, form);
            if (tag.isEmpty()) return;
            ResourceLocation folder = folderTag(tag.get());
            if (!members.containsKey(tag.get()) && !members.containsKey(folder)) return;
            List<ResourceLocation> items = new ArrayList<>(List.of(f.canonical().get()));
            if (!isUnified(f)) items.addAll(f.alternatives());
            for (ResourceLocation item : items) {
                if (!members.getOrDefault(tag.get(), Set.of()).contains(item)) out.computeIfAbsent(tag.get(), t -> new TreeSet<>()).add(item);
                if (members.containsKey(folder) && !members.get(folder).contains(item)) out.computeIfAbsent(folder, t -> new TreeSet<>()).add(item);
            }
        }));
    }

    /**
     * MNX-079: discovery sees the items the player added to a form's tag as members of it (call after
     * {@link #restoreRemovedMembers}). Removed ones stay: the resolver lists them as taken out of the tag.
     */
    public static void addPlayerTags(Map<ResourceLocation, List<ResourceLocation>> tagMembers, ResolutionPolicy policy) {
        policy.tagEdits().forEach((key, edit) -> TagDiscovery.conventionTag(key.material(), key.form()).ifPresent(tag -> {
            List<ResourceLocation> members = new ArrayList<>(tagMembers.getOrDefault(tag, List.of()));
            edit.add().stream().sorted().filter(i -> !members.contains(i)).forEach(members::add);
            if (!members.isEmpty()) tagMembers.put(tag, members);
        }));
    }

    /** c:ingots/tin to c:ingots. */
    private static ResourceLocation folderTag(ResourceLocation tag) {
        return ResourceLocation.fromNamespaceAndPath(tag.getNamespace(), tag.getPath().substring(0, tag.getPath().indexOf('/')));
    }

    /**
     * What {@link #missingTags} reads: the members of each discovered form's convention tag and folder tag, as they are
     * without Material Nexus (call after {@link #restoreRemovedMembers}). A tag the game does not have is absent.
     */
    public static Map<ResourceLocation, Set<ResourceLocation>> conventionMembers(
            dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials discovered, Map<ResourceLocation, ? extends Collection<ResourceLocation>> tagMembers) {
        Map<ResourceLocation, Set<ResourceLocation>> out = new java.util.HashMap<>();
        discovered.materials().forEach((material, forms) -> forms.keySet().forEach(form ->
                TagDiscovery.conventionTag(material, form).ifPresent(tag -> {
                    for (ResourceLocation t : List.of(tag, folderTag(tag))) {
                        Collection<ResourceLocation> m = tagMembers.get(t);
                        if (m != null && !m.isEmpty()) out.put(t, Set.copyOf(m));
                    }
                })));
        return Map.copyOf(out);
    }

    /** NeoForge tag file that appends and removes: the items keep every other tag they have. */
    private static JsonObject tagFile(Collection<ResourceLocation> added, Collection<ResourceLocation> removed) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray values = new JsonArray();
        // Optional entries: an added item whose mod left the pack must not make the whole tag fail to load.
        added.forEach(i -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", i.toString());
            entry.addProperty("required", false);
            values.add(entry);
        });
        tag.add("values", values);
        if (!removed.isEmpty()) {
            JsonArray remove = new JsonArray();
            removed.forEach(i -> remove.add(i.toString()));
            tag.add("remove", remove);
        }
        return tag;
    }

    private static JsonObject conversionRecipe(ResourceLocation from, ResourceLocation to) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", from.toString());
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient);
        JsonObject result = new JsonObject();
        result.addProperty("count", 1);
        result.addProperty("item", to.toString());
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");
        recipe.addProperty("category", "misc");
        recipe.addProperty("group", "materialnexus_conversion");
        recipe.add("ingredients", ingredients);
        recipe.add("result", result);
        return recipe;
    }

    public static JsonArray toJson(List<Effect> effects) {
        JsonArray array = new JsonArray();
        for (Effect e : effects) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", e.kind());
            o.addProperty("target", e.target().toString());
            o.addProperty("item", e.item().toString());
            array.add(o);
        }
        return array;
    }

    /** Effects recorded in the current generated pack; empty when there is none yet. */
    public static List<Effect> readManifest(Path generatedDir) throws IOException {
        Path manifest = generatedDir.resolve(GeneratedPack.MANIFEST);
        if (!Files.isRegularFile(manifest)) return List.of();
        JsonObject root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Effect> effects = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("changes")) {
            JsonObject o = el.getAsJsonObject();
            effects.add(new Effect(o.get("kind").getAsString(),
                    ResourceLocation.parse(o.get("target").getAsString()), ResourceLocation.parse(o.get("item").getAsString())));
        }
        return effects;
    }

    /**
     * ADR-010 for tags: put back the members Material Nexus itself removed, so discovery keeps
     * seeing the pre-MNX state and the next apply regenerates the same removals. The members it added (MNX-078) are
     * taken out the same way; a tag left with no member is dropped, as it did not exist.
     */
    public static void restoreRemovedMembers(Map<ResourceLocation, List<ResourceLocation>> tagMembers, List<Effect> effects) {
        for (Effect e : effects) {
            boolean removed = e.kind().equals(TAG_REMOVE);
            if (!removed && !e.kind().equals(TAG_ADD)) continue;
            List<ResourceLocation> members = new ArrayList<>(tagMembers.getOrDefault(e.target(), List.of()));
            if (removed && !members.contains(e.item())) members.add(e.item());
            if (!removed) members.remove(e.item());
            if (members.isEmpty()) tagMembers.remove(e.target());
            else tagMembers.put(e.target(), members);
        }
    }
}
