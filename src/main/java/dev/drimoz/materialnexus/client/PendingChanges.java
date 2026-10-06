package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.CanonicalChange;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical choices made in the GUI but not yet applied. Client-side only; the server stays stateless. */
final class PendingChanges {
    private static final Map<String, CanonicalChange> CHANGES = new LinkedHashMap<>();

    private PendingChanges() { }

    static Optional<ResourceLocation> get(String material, String form) {
        return Optional.ofNullable(CHANGES.get(key(material, form))).map(CanonicalChange::provider);
    }

    /** A pending choice; accepting a suggestion is a choice too, so the provider may equal the shown canonical. */
    static void set(String material, String form, ResourceLocation provider) {
        CHANGES.put(key(material, form), new CanonicalChange(material, form, provider));
    }

    static void clear(String material, String form) {
        CHANGES.remove(key(material, form));
    }

    static List<CanonicalChange> all() { return List.copyOf(CHANGES.values()); }

    static int size() { return CHANGES.size(); }

    static void clear() { CHANGES.clear(); }

    private static String key(String material, String form) { return material + "/" + form; }
}
