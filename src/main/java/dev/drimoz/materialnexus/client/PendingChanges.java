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

    /** Drops one data edit by its "kind|id" key (MNX-058). */
    static void clearData(String key) { DATA.remove(key); }

    static Map<String, Optional<String>> data() { return Map.copyOf(DATA); }

    /** Items to create for missing forms (MNX-039), as "material/form". Toggles. */
    static boolean creating(String material, String form) { return CREATIONS.contains(material + "/" + form); }

    static void toggleCreation(String material, String form) {
        if (!CREATIONS.remove(material + "/" + form)) CREATIONS.add(material + "/" + form);
    }

    static List<String> creations() { return List.copyOf(CREATIONS); }

    /** Drops one item creation by its "material/form" key (MNX-058). */
    static void toggleCreation(String key) {
        if (!CREATIONS.remove(key)) CREATIONS.add(key);
    }

    /** MNX-058: pending choices and items to create of a material, or of a form when {@code material} is null. */
    static int countFor(String material, String form) {
        return (int) (CHANGES.values().stream().filter(c -> matches(c.material(), c.form(), material, form)).count()
                + CREATIONS.stream().filter(k -> matches(k.split("/", 2)[0], k.split("/", 2)[1], material, form)).count()
                + (material == null && PROCESSES.containsKey(form) ? 1 : 0));
    }

    /** MNX-058: drops what {@link #countFor} counts. */
    static void clearFor(String material, String form) {
        CHANGES.values().removeIf(c -> matches(c.material(), c.form(), material, form));
        CREATIONS.removeIf(k -> matches(k.split("/", 2)[0], k.split("/", 2)[1], material, form));
        if (material == null) PROCESSES.remove(form);
    }

    private static boolean matches(String m, String f, String material, String form) {
        return material != null ? m.equals(material) : f.equals(form);
    }

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
        boolean empty = value.isJsonArray() && value.getAsJsonArray().isEmpty() || value.isJsonObject() && value.getAsJsonObject().size() == 0
                || value.equals(new com.google.gson.JsonPrimitive(false));
        if (value.equals(baseGlobal.get(field)) || (empty && !baseGlobal.has(field))) GLOBAL_PATCH.remove(field);
        else GLOBAL_PATCH.add(field, value);
    }

    static boolean globalChanged(String field) { return GLOBAL_PATCH.has(field); }

    /**
     * MNX-058 "everything back to default": empty not-same list, conversion recipes and process rules. The mod priority
     * is kept (MNX-061): it has its own reset, on the Home view.
     */
    static void resetSettings() {
        for (String field : List.of("not_same", "conversion_recipes")) setGlobal(field, new com.google.gson.JsonArray());
        setGlobal("add_missing_tags", new com.google.gson.JsonPrimitive(false));
        setGlobal("tag_edits", new com.google.gson.JsonObject());
        if (baseGlobal.get("processes") instanceof com.google.gson.JsonObject rules) {
            rules.keySet().forEach(form -> PROCESSES.put(form, new ProcessRules.Rule(List.of(), false, false)));
        }
    }

    /** MNX-058: each pending global.json edit in words, with what drops it (one line per not_same item). */
    static List<Map.Entry<net.minecraft.network.chat.Component, Runnable>> globalEdits() {
        List<Map.Entry<net.minecraft.network.chat.Component, Runnable>> out = new java.util.ArrayList<>();
        for (String field : List.copyOf(GLOBAL_PATCH.keySet())) {
            if (!field.equals("not_same")) {
                net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.translatableWithFallback(
                        "screen.materialnexus.presets.field." + field, field);
                out.add(Map.entry(text, () -> GLOBAL_PATCH.remove(field)));
                continue;
            }
            com.google.gson.JsonArray before = baseGlobal.get(field) instanceof com.google.gson.JsonArray a ? a : new com.google.gson.JsonArray();
            com.google.gson.JsonArray after = GLOBAL_PATCH.getAsJsonArray(field);
            List<String> changed = new java.util.ArrayList<>();
            after.forEach(e -> { if (!before.contains(e)) changed.add(e.getAsString()); });
            before.forEach(e -> { if (!after.contains(e)) changed.add(e.getAsString()); });
            for (String item : changed) {
                ResourceLocation id = ResourceLocation.tryParse(item);
                if (id == null) continue;
                String key = notSame(id) ? "screen.materialnexus.preview_not_same" : "screen.materialnexus.preview_same_again";
                out.add(Map.entry(net.minecraft.network.chat.Component.translatable(key, Names.stack(id).getHoverName()), () -> toggleNotSame(id)));
            }
        }
        return out;
    }

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
            if (field.equals("mod_priority") && GLOBAL_PATCH.get(field).isJsonArray()) {
                List<String> order = new java.util.ArrayList<>();
                GLOBAL_PATCH.getAsJsonArray(field).forEach(e -> order.add(e.getAsString()));
                lines.add(net.minecraft.network.chat.Component.translatable("screen.materialnexus.preview_priority", String.join(" > ", order)));
                continue;
            }
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
                GLOBAL_PATCH.size() == 0 ? Optional.empty() : Optional.of(GLOBAL_PATCH.toString()));
    }

    private static String key(String material, String form) { return material + "/" + form; }

    // ---- tag edits (MNX-079): global.json "tag_edits": {"tin/ingot": {"add": [...], "remove": [...]}} ----

    private static boolean tagListed(String material, String form, String list, ResourceLocation item) {
        return global("tag_edits") instanceof com.google.gson.JsonObject edits && edits.get(material + "/" + form) instanceof com.google.gson.JsonObject edit
                && edit.get(list) instanceof com.google.gson.JsonArray a && a.contains(new com.google.gson.JsonPrimitive(item.toString()));
    }

    static boolean tagRemoved(String material, String form, ResourceLocation item) { return tagListed(material, form, "remove", item); }

    static boolean tagAdded(String material, String form, ResourceLocation item) { return tagListed(material, form, "add", item); }

    /** Puts the item in or out of one list of the form's edit; empty lists and edits are dropped. */
    private static void toggleTagEdit(String material, String form, String list, ResourceLocation item) {
        com.google.gson.JsonObject edits = global("tag_edits") instanceof com.google.gson.JsonObject o ? o.deepCopy() : new com.google.gson.JsonObject();
        String key = material + "/" + form;
        com.google.gson.JsonObject edit = edits.get(key) instanceof com.google.gson.JsonObject o ? o : new com.google.gson.JsonObject();
        com.google.gson.JsonArray items = edit.get(list) instanceof com.google.gson.JsonArray a ? a : new com.google.gson.JsonArray();
        com.google.gson.JsonPrimitive id = new com.google.gson.JsonPrimitive(item.toString());
        if (!items.remove(id)) items.add(id);
        if (items.isEmpty()) edit.remove(list);
        else edit.add(list, items);
        if (edit.size() == 0) edits.remove(key);
        else edits.add(key, edit);
        setGlobal("tag_edits", edits);
    }

    /** Shift + right click: undoes an addition, else takes the item out of the form's tag (or puts it back). */
    static void toggleTagRemoval(String material, String form, ResourceLocation item) {
        toggleTagEdit(material, form, tagAdded(material, form, item) ? "add" : "remove", item);
    }

    /** Data view: puts an item in a form's tag (pending); a pending removal of it is cancelled instead. */
    static void addToTag(String material, String form, ResourceLocation item) {
        if (tagRemoved(material, form, item)) toggleTagEdit(material, form, "remove", item);
        else if (!tagAdded(material, form, item)) toggleTagEdit(material, form, "add", item);
    }
}
