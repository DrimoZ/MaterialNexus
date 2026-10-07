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

    static int size() { return CHANGES.size() + PROCESSES.size() + CREATIONS.size() + DATA.size() + GLOBAL_PATCH.size(); }

    static void clear() {
        CHANGES.clear();
        PROCESSES.clear();
        CREATIONS.clear();
        DATA.clear();
        GLOBAL_PATCH.entrySet().clear();
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

    // ---- global.json fields (MNX-050): edited as a patch over the file the server sent ----

    private static com.google.gson.JsonObject baseGlobal = new com.google.gson.JsonObject();
    private static final com.google.gson.JsonObject GLOBAL_PATCH = new com.google.gson.JsonObject();

    static void baseGlobal(String json) {
        try {
            baseGlobal = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            baseGlobal = new com.google.gson.JsonObject();
        }
    }

    /** A field of global.json as it will be after Apply: the pending value, else the written one. */
    static com.google.gson.JsonElement global(String field) {
        return GLOBAL_PATCH.has(field) ? GLOBAL_PATCH.get(field) : baseGlobal.get(field);
    }

    /** Replaces a whole field; setting it back to the written value drops the pending change. */
    static void setGlobal(String field, com.google.gson.JsonElement value) {
        if (value.equals(baseGlobal.get(field)) || (value.isJsonArray() && value.getAsJsonArray().isEmpty() && !baseGlobal.has(field))) GLOBAL_PATCH.remove(field);
        else GLOBAL_PATCH.add(field, value);
    }

    static boolean globalChanged(String field) { return GLOBAL_PATCH.has(field); }

    /** "Not the same item" (MNX-050), as it will be after Apply. */
    static boolean notSame(ResourceLocation item) {
        com.google.gson.JsonElement list = global("not_same");
        return list != null && list.isJsonArray() && list.getAsJsonArray().contains(new com.google.gson.JsonPrimitive(item.toString()));
    }

    static void toggleNotSame(ResourceLocation item) {
        com.google.gson.JsonArray list = global("not_same") instanceof com.google.gson.JsonArray a ? a.deepCopy() : new com.google.gson.JsonArray();
        com.google.gson.JsonPrimitive id = new com.google.gson.JsonPrimitive(item.toString());
        if (!list.remove(id)) list.add(id);
        setGlobal("not_same", list);
    }

    /** The pending global.json edits in words, for Preview: items (un)marked as not the same, other fields changed. */
    static List<net.minecraft.network.chat.Component> globalLines() {
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        for (String field : GLOBAL_PATCH.keySet()) {
            if (!field.equals("not_same")) {
                lines.add(net.minecraft.network.chat.Component.translatable("screen.materialnexus.preview_global", field));
                continue;
            }
            com.google.gson.JsonArray before = baseGlobal.get(field) instanceof com.google.gson.JsonArray a ? a : new com.google.gson.JsonArray();
            com.google.gson.JsonArray after = GLOBAL_PATCH.getAsJsonArray(field);
            after.forEach(e -> { if (!before.contains(e)) lines.add(net.minecraft.network.chat.Component.translatable("screen.materialnexus.preview_not_same", e.getAsString())); });
            before.forEach(e -> { if (!after.contains(e)) lines.add(net.minecraft.network.chat.Component.translatable("screen.materialnexus.preview_same_again", e.getAsString())); });
        }
        return lines;
    }

    /** Every pending change as one request (Preview, or Apply with {@code apply}). */
    static dev.drimoz.materialnexus.network.PreviewRequest request(boolean apply, Optional<ResourceLocation> preset) {
        return new dev.drimoz.materialnexus.network.PreviewRequest(all(), apply, preset, processes(), creations(), data(),
                GLOBAL_PATCH.isEmpty() ? Optional.empty() : Optional.of(GLOBAL_PATCH.toString()));
    }

    private static String key(String material, String form) { return material + "/" + form; }
}
