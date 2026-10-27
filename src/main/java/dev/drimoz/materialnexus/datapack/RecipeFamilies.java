package dev.drimoz.materialnexus.datapack;

import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.integration.RecipeFormats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

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
    /** {@code REMOVED}: removed by the player (MNX-117); {@code PACK_DISABLED}: switched off by a datapack (MNX-118). Neither is loaded. */
    public enum Status { CANONICAL, ALTERNATIVE, OTHER, REWRITTEN, DISABLED, UNSUPPORTED, REMOVED, PACK_DISABLED }

    /**
     * Whether the player can remove a recipe from the screen (MNX-117): only one with a file the generated pack can
     * override. A recipe made in memory by a script has none; one Material Nexus generates comes from a rule; one a
     * datapack switches off is already off.
     */
    public enum Removal { ALLOWED, IN_MEMORY, GENERATED, PACK }

    /**
     * {@code replaced}: for a rewritten recipe, the alternative it made before Material Nexus (MNX-088). {@code editedBy}:
     * who else already changes this recipe: "kubejs" for a script (MNX-117), else the id of the datapack replacing it (MNX-118).
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
        PackEdits.Edits packs = PackEdits.of(server);
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
                        java.util.Optional.empty(), removal(server, e.target()), editedBy(e.target(), scripted, packs)));
            }
        }

        RecipeFormats formats = RecipeFormats.load(server.getResourceManager());
        // MNX-118: a recipe a datapack switched off is not loaded either; what it made is read under the datapack.
        packs.recipes().forEach((id, pack) -> {
            if (server.getRecipeManager().byKey(id).isPresent()) return;
            PackEdits.beneath(server, id).ifPresent(json -> RecipeRewrites.outputIds(json, formats).stream().filter(members::contains).findFirst()
                    .ifPresent(output -> rows.add(new Row(id, json.has("type") ? java.util.Objects.requireNonNullElse(ResourceLocation.tryParse(json.get("type").getAsString()), disabledType) : disabledType,
                            output, Status.PACK_DISABLED, java.util.Optional.empty(), Removal.PACK, java.util.Optional.of(pack)))));
        });
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.getSerializer());
            ItemStack out = holder.getResultItem(server.registryAccess());
            List<ResourceLocation> outputs = new ArrayList<>();
            if (!out.isEmpty()) {
                outputs.add(BuiltInRegistries.ITEM.getKey(out.getItem()));
            } else if (type != null && formats.forType(type).isPresent()) {
                RecipeSources.originalJson(server, holder.getId()).ifPresent(json -> outputs.addAll(RecipeRewrites.outputIds(json, formats)));
            }
            for (ResourceLocation output : outputs) {
                if (!members.contains(output)) continue;
                boolean known = type != null && formats.forType(type).isPresent();
                Status status = rewritten.contains(holder.getId()) ? Status.REWRITTEN
                        : form.canonical().map(output::equals).orElse(false) ? Status.CANONICAL
                        : alternatives.contains(output) ? (known ? Status.ALTERNATIVE : Status.UNSUPPORTED)
                        : Status.OTHER;
                java.util.Optional<ResourceLocation> replaced = status != Status.REWRITTEN ? java.util.Optional.empty()
                        : RecipeSources.originalJson(server, holder.getId()).stream()
                                .flatMap(json -> RecipeRewrites.outputIds(json, formats).stream()).filter(alternatives::contains).findFirst();
                rows.add(new Row(holder.getId(), type == null ? holder.getId() : type, output, status, replaced, removal(server, holder.getId()), editedBy(holder.getId(), scripted, packs)));
                break;
            }
        }
        rows.sort(Comparator.comparing(Row::status).thenComparing(Row::recipe));
        return rows;
    }

    /** A script first: it acts on what the datapacks left. */
    private static java.util.Optional<String> editedBy(ResourceLocation recipe, Set<ResourceLocation> scripted, PackEdits.Edits packs) {
        return scripted.contains(recipe) ? java.util.Optional.of("kubejs") : java.util.Optional.ofNullable(packs.recipes().get(recipe));
    }
}
