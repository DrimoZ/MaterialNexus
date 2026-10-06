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
