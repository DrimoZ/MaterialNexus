package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Builds GUI views from the active snapshot, by material or by form (MNX-035). Client input is untrusted:
 * pages are clamped, names validated.
 */
public final class NexusQueries {
    public static final int PAGE_SIZE = 50;
    public static final int MAX_QUERY = 64;

    private NexusQueries() { }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query) {
        return listPage(snapshot, page, query, false);
    }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query, boolean byForm) {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<MaterialListPayload.Summary> matches = groups(snapshot, byForm).entrySet().stream()
                .filter(e -> e.getKey().contains(filter))
                .map(e -> summarize(e.getKey(), e.getValue().values()))
                .toList();
        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clamped = Math.clamp(page, 0, pageCount - 1);
        int from = clamped * PAGE_SIZE;
        return new MaterialListPayload(clamped, pageCount, matches.size(), filter, byForm,
                matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())));
    }

    public static Optional<MaterialDetailPayload> detail(ResolvedSnapshot snapshot, String material) {
        return detail(snapshot, material, false);
    }

    /** One material's forms, or with {@code byForm} one form across every material that has it. */
    public static Optional<MaterialDetailPayload> detail(ResolvedSnapshot snapshot, String name, boolean byForm) {
        boolean valid = byForm ? FormId.read(name).result().isPresent() : MaterialId.read(name).result().isPresent();
        if (!valid) return Optional.empty();
        Map<String, ResolvedForm> group = groups(snapshot, byForm).get(name);
        if (group == null) return Optional.empty();
        return Optional.of(new MaterialDetailPayload(name, byForm, group.entrySet().stream()
                .map(e -> byForm ? new MaterialDetailPayload.FormView(e.getKey(), name, e.getValue())
                        : new MaterialDetailPayload.FormView(name, e.getKey(), e.getValue()))
                .toList(), List.of()));
    }

    /** name → (other axis name → resolved form). By material it is the snapshot itself; by form it is transposed. */
    private static Map<String, Map<String, ResolvedForm>> groups(ResolvedSnapshot snapshot, boolean byForm) {
        // ponytail: rebuilt per request; fine for a GUI click, cache in the snapshot if packs get huge
        Map<String, Map<String, ResolvedForm>> groups = new TreeMap<>();
        snapshot.materials().values().forEach(m -> m.forms().forEach((form, resolved) -> groups
                .computeIfAbsent(byForm ? form.name() : m.material().name(), k -> new TreeMap<>())
                .put(byForm ? m.material().name() : form.name(), resolved)));
        return groups;
    }

    private static MaterialListPayload.Summary summarize(String name, Collection<ResolvedForm> forms) {
        int providers = 0;
        int duplicates = 0;
        int unified = 0;
        for (ResolvedForm form : forms) {
            providers += (form.canonical().isPresent() ? 1 : 0) + form.alternatives().size() + form.notUnified().size();
            if (!form.alternatives().isEmpty()) duplicates++;
            if (!form.alternatives().isEmpty() && form.source() != PolicyPrecedence.DEFAULT) unified++;
        }
        return new MaterialListPayload.Summary(name, forms.size(), providers, duplicates, unified);
    }
}
