package dev.drimoz.materialnexus.client;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client copy of the applied alternatives, for recipe viewer adapters. Viewers subscribe; this class
 * never references them, so it is safe whichever viewer (or none) is installed.
 */
public final class UnifiedItemsClient {
    private static volatile Set<Identifier> alternatives = Set.of();
    private static volatile java.util.Map<Identifier, Identifier> becomes = java.util.Map.of();
    private static volatile Set<Identifier> kept = Set.of();

    /** MNX-054: what an alternative becomes, for tooltips. */
    public static java.util.Map<Identifier, Identifier> becomes() {
        return becomes;
    }

    public static boolean kept(Identifier item) {
        return kept.contains(item);
    }

    public static void conversions(java.util.Map<Identifier, Identifier> latest) {
        becomes = java.util.Map.copyOf(latest);
        kept = Set.copyOf(latest.values());
    }
    private static final List<Consumer<Set<Identifier>>> LISTENERS = new CopyOnWriteArrayList<>();

    private UnifiedItemsClient() { }

    public static Set<Identifier> alternatives() {
        return alternatives;
    }

    public static void update(Set<Identifier> latest) {
        alternatives = Set.copyOf(latest);
        LISTENERS.forEach(l -> l.accept(alternatives));
    }

    public static void subscribe(Consumer<Set<Identifier>> listener) {
        LISTENERS.add(listener);
    }
}
