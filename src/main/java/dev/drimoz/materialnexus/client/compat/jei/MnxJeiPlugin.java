package dev.drimoz.materialnexus.client.compat.jei;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.client.UnifiedItemsClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JEI adapter (MNX-013): hides the alternatives of the applied unification and shows them again if a
 * revert brings them back. Only JEI instantiates this class, so nothing here loads when JEI is absent.
 */
@JeiPlugin
public final class MnxJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "jei");

    private IJeiRuntime runtime;
    /** What this runtime currently hides; a new runtime (JEI restarts on reload) starts with nothing hidden. */
    private final Set<ResourceLocation> hidden = new HashSet<>();
    private boolean subscribed;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        hidden.clear();
        if (!subscribed) {
            UnifiedItemsClient.subscribe(this::apply);
            subscribed = true;
        }
        apply(UnifiedItemsClient.alternatives());
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        hidden.clear();
    }

    private void apply(Set<ResourceLocation> alternatives) {
        if (runtime == null) return;
        Set<ResourceLocation> show = new HashSet<>(hidden);
        show.removeAll(alternatives);
        Set<ResourceLocation> hide = new HashSet<>(alternatives);
        hide.removeAll(hidden);
        if (!show.isEmpty()) runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks(show));
        if (!hide.isEmpty()) runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks(hide));
        hidden.removeAll(show);
        hidden.addAll(hide);
    }

    private static List<ItemStack> stacks(Set<ResourceLocation> ids) {
        return ids.stream().flatMap(id -> BuiltInRegistries.ITEM.getOptional(id).stream()).map(ItemStack::new).toList();
    }
}
