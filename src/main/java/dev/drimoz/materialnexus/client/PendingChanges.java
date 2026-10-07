package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical choices and process rule edits made in the GUI but not yet applied. Client-side only; the server stays stateless. */
final class PendingChanges {
    private static final Map<String, CanonicalChange> CHANGES = new LinkedHashMap<>();
    private static final Map<String, ProcessRules.Rule> PROCESSES = new LinkedHashMap<>();
    private static final java.util.Set<String> CREATIONS = new java.util.TreeSet<>();
    /** MNX-046: "kind|id" to new text, or empty to go back to the original. */
    private static final Map<String, Optional<String>> DATA = new LinkedHashMap<>();

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

    static int size() { return CHANGES.size() + PROCESSES.size() + CREATIONS.size() + DATA.size(); }

    static void clear() {
        CHANGES.clear();
        PROCESSES.clear();
        CREATIONS.clear();
        DATA.clear();
    }

    static Optional<Optional<String>> data(String kind, String id) { return Optional.ofNullable(DATA.get(kind + "|" + id)); }

    static void setData(String kind, String id, Optional<String> text) { DATA.put(kind + "|" + id, text); }

    static void clearData(String kind, String id) { DATA.remove(kind + "|" + id); }

    static Map<String, Optional<String>> data() { return Map.copyOf(DATA); }

    /** Items to create for missing forms (MNX-039), as "material/form". Toggles. */
    static boolean creating(String material, String form) { return CREATIONS.contains(material + "/" + form); }

    static void toggleCreation(String material, String form) {
        if (!CREATIONS.remove(material + "/" + form)) CREATIONS.add(material + "/" + form);
    }

    static List<String> creations() { return List.copyOf(CREATIONS); }

    static Optional<ProcessRules.Rule> process(String form) { return Optional.ofNullable(PROCESSES.get(form)); }

    static void setProcess(String form, ProcessRules.Rule rule) { PROCESSES.put(form, rule); }

    static void clearProcess(String form) { PROCESSES.remove(form); }

    static Map<String, ProcessRules.Rule> processes() { return Map.copyOf(PROCESSES); }

    private static String key(String material, String form) { return material + "/" + form; }
}
