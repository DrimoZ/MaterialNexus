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
    public enum Status { CANONICAL, ALTERNATIVE, OTHER, REWRITTEN, DISABLED, UNSUPPORTED }

    public record Row(ResourceLocation recipe, ResourceLocation type, ResourceLocation output, Status status) { }

    private RecipeFamilies() { }

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
        List<Row> rows = new ArrayList<>();
        for (PackContent.Effect e : manifest) {
            if (e.kind().equals(RecipeRewrites.REWRITE)) rewritten.add(e.target());
            if (e.kind().equals(RecipeRewrites.DISABLE) && members.contains(e.item())) {
                rows.add(new Row(e.target(), ResourceLocation.fromNamespaceAndPath("materialnexus", "disabled"), e.item(), Status.DISABLED));
            }
        }

        RecipeFormats formats = RecipeFormats.load(server.getResourceManager());
        for (Recipe<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.getSerializer());
            ItemStack out = holder.getResultItem(server.registryAccess());
            List<ResourceLocation> outputs = new ArrayList<>();
            if (!out.isEmpty()) {
                outputs.add(BuiltInRegistries.ITEM.getKey(out.getItem()));
            } else if (type != null && formats.forType(type).isPresent()) {
                RecipeSources.originalJson(server.getResourceManager(), holder.getId()).ifPresent(json -> outputs.addAll(RecipeRewrites.outputIds(json, formats)));
            }
            for (ResourceLocation output : outputs) {
                if (!members.contains(output)) continue;
                boolean known = type != null && formats.forType(type).isPresent();
                Status status = rewritten.contains(holder.getId()) ? Status.REWRITTEN
                        : form.canonical().map(output::equals).orElse(false) ? Status.CANONICAL
                        : alternatives.contains(output) ? (known ? Status.ALTERNATIVE : Status.UNSUPPORTED)
                        : Status.OTHER;
                rows.add(new Row(holder.getId(), type == null ? holder.getId() : type, output, status));
                break;
            }
        }
        rows.sort(Comparator.comparing(Row::status).thenComparing(Row::recipe));
        return rows;
    }
}
