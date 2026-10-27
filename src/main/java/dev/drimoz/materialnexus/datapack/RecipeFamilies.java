package dev.drimoz.materialnexus.datapack;

import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recipe family of one material form (MNX-009): every loaded recipe producing one of its providers, with
 * what Material Nexus makes of it. Recipes it disabled are no longer loaded and come from the manifest.
 * Computed when the family is requested, never per tick.
 */
public final class RecipeFamilies {
    /** {@code REMOVED}: removed by the player (MNX-117), no longer loaded. */
    public enum Status { CANONICAL, ALTERNATIVE, OTHER, REWRITTEN, DISABLED, UNSUPPORTED, REMOVED }

    /**
     * Whether the player can remove a recipe from the screen (MNX-117): only one with a file the generated pack can
     * override. A recipe made in memory by a script has none; one Material Nexus generates comes from a rule.
     */
    public enum Removal { ALLOWED, IN_MEMORY, GENERATED }

    /**
     * {@code replaced}: for a rewritten recipe, the alternative it made before Material Nexus (MNX-088). {@code editedBy}:
     * who else already changes this recipe, "kubejs" for a script (MNX-117).
     */
    public record Row(ResourceLocation recipe, ResourceLocation type, ResourceLocation output, Status status, java.util.Optional<ResourceLocation> replaced,
                      Removal removal, java.util.Optional<String> editedBy) { }

    private RecipeFamilies() { }

    private static Removal removal(MinecraftServer server, ResourceLocation id) {
        List<String> packs = RecipeSources.packs(server, id);
        return RecipeSources.removable(packs) ? Removal.ALLOWED : packs.isEmpty() ? Removal.IN_MEMORY : Removal.GENERATED;
    }

    public static List<Row> family(MinecraftServer server, ResolvedForm form) {
        Set<ResourceLocation> alternatives = new HashSet<>(form.alternatives());
        Set<ResourceLocation> members = new HashSet<>(alternatives);
        form.canonical().ifPresent(members::add);
        form.notUnified().forEach(n -> members.add(n.item()));

        List<PackContent.Effect> manifest;
        try {
            manifest = PackContent.readManifest(MnxPaths.generated());
        } catch (java.io.IOException e) {
            manifest = List.of();
        }
        Set<ResourceLocation> rewritten = new HashSet<>();
        Set<ResourceLocation> scripted = new HashSet<>();
        dev.drimoz.materialnexus.core.resolution.SnapshotManager.current().scripts().recipes().stream()
                .filter(r -> r.kind() == dev.drimoz.materialnexus.core.scripts.ScriptChanges.Kind.CHANGED).forEach(r -> scripted.add(r.recipe()));
        List<Row> rows = new ArrayList<>();
        ResourceLocation disabledType = ResourceLocation.fromNamespaceAndPath("materialnexus", "disabled");
        for (PackContent.Effect e : manifest) {
            if (e.kind().equals(RecipeRewrites.REWRITE)) rewritten.add(e.target());
            boolean player = e.kind().equals(PackContent.PLAYER_DISABLE);
            if ((player || e.kind().equals(RecipeRewrites.DISABLE)) && members.contains(e.item())) {
                // Not loaded any more: the type comes from the file beneath the generated pack.
                ResourceLocation type = !player ? disabledType : RecipeSources.originalJson(server, e.target())
                        .map(json -> json.has("type") ? ResourceLocation.tryParse(json.get("type").getAsString()) : null).orElse(disabledType);
                rows.add(new Row(e.target(), type == null ? disabledType : type, e.item(), player ? Status.REMOVED : Status.DISABLED,
                        java.util.Optional.empty(), removal(server, e.target()), editedBy(e.target(), scripted)));
            }
        }

        RecipeFormats formats = RecipeFormats.load(server.getResourceManager());
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            ItemStack out = holder.value().getResultItem(server.registryAccess());
            List<ResourceLocation> outputs = new ArrayList<>();
            if (!out.isEmpty()) {
                outputs.add(BuiltInRegistries.ITEM.getKey(out.getItem()));
            } else if (type != null && formats.forType(type).isPresent()) {
                RecipeSources.originalJson(server, holder.id()).ifPresent(json -> outputs.addAll(RecipeRewrites.outputIds(json, formats)));
            }
            for (ResourceLocation output : outputs) {
                if (!members.contains(output)) continue;
                boolean known = type != null && formats.forType(type).isPresent();
                Status status = rewritten.contains(holder.id()) ? Status.REWRITTEN
                        : form.canonical().map(output::equals).orElse(false) ? Status.CANONICAL
                        : alternatives.contains(output) ? (known ? Status.ALTERNATIVE : Status.UNSUPPORTED)
                        : Status.OTHER;
                java.util.Optional<ResourceLocation> replaced = status != Status.REWRITTEN ? java.util.Optional.empty()
                        : RecipeSources.originalJson(server, holder.id()).stream()
                                .flatMap(json -> RecipeRewrites.outputIds(json, formats).stream()).filter(alternatives::contains).findFirst();
                rows.add(new Row(holder.id(), type == null ? holder.id() : type, output, status, replaced, removal(server, holder.id()), editedBy(holder.id(), scripted)));
                break;
            }
        }
        rows.sort(Comparator.comparing(Row::status).thenComparing(Row::recipe));
        return rows;
    }

    private static java.util.Optional<String> editedBy(ResourceLocation recipe, Set<ResourceLocation> scripted) {
        return scripted.contains(recipe) ? java.util.Optional.of("kubejs") : java.util.Optional.empty();
    }
}
