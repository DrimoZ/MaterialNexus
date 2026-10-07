package dev.drimoz.materialnexus.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client copy of the applied alternatives, for recipe viewer adapters. Viewers subscribe; this class
 * never references them, so it is safe whichever viewer (or none) is installed.
 */
public final class UnifiedItemsClient {
    private static volatile Set<ResourceLocation> alternatives = Set.of();
    private static volatile java.util.Map<ResourceLocation, ResourceLocation> becomes = java.util.Map.of();
    private static volatile Set<ResourceLocation> kept = Set.of();

    /** MNX-054: what an alternative becomes, for tooltips. */
    public static java.util.Map<ResourceLocation, ResourceLocation> becomes() {
        return becomes;
    }

    public static boolean kept(ResourceLocation item) {
        return kept.contains(item);
    }

    public static void conversions(java.util.Map<ResourceLocation, ResourceLocation> latest) {
        becomes = java.util.Map.copyOf(latest);
        kept = Set.copyOf(latest.values());
    }
    private static final List<Consumer<Set<ResourceLocation>>> LISTENERS = new CopyOnWriteArrayList<>();

    private UnifiedItemsClient() { }

    public static Set<ResourceLocation> alternatives() {
        return alternatives;
    }

    public static void update(Set<ResourceLocation> latest) {
        alternatives = Set.copyOf(latest);
        LISTENERS.forEach(l -> l.accept(alternatives));
    }

    public static void subscribe(Consumer<Set<ResourceLocation>> listener) {
        LISTENERS.add(listener);
    }
}
