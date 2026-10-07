package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import dev.drimoz.materialnexus.core.resolution.ResolvedSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds GUI views from the active snapshot. Client input is untrusted: pages are clamped, names validated. */
public final class NexusQueries {
    public static final int PAGE_SIZE = 50;
    public static final int MAX_QUERY = 64;

    private NexusQueries() { }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query) {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<MaterialListPayload.Summary> matches = snapshot.materials().values().stream()
                .filter(m -> m.material().name().contains(filter))
                .map(NexusQueries::summarize)
                .toList();
        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clamped = Math.clamp(page, 0, pageCount - 1);
        int from = clamped * PAGE_SIZE;
        return new MaterialListPayload(clamped, pageCount, matches.size(), filter,
                matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())));
    }

    public static Optional<MaterialDetailPayload> detail(ResolvedSnapshot snapshot, String material) {
        return MaterialId.read(material).result()
                .map(id -> snapshot.materials().get(id))
                .map(m -> new MaterialDetailPayload(m.material().name(), m.forms().entrySet().stream()
                        .map(e -> new MaterialDetailPayload.FormView(e.getKey().name(), e.getValue()))
                        .toList(), java.util.List.of()));
    }

    private static MaterialListPayload.Summary summarize(ResolvedMaterial m) {
        int providers = 0;
        int duplicates = 0;
        int unified = 0;
        for (var form : m.forms().values()) {
            providers += (form.canonical().isPresent() ? 1 : 0) + form.alternatives().size() + form.notUnified().size();
            if (!form.alternatives().isEmpty()) duplicates++;
            if (!form.alternatives().isEmpty() && form.source() != dev.drimoz.materialnexus.core.policy.PolicyPrecedence.DEFAULT) unified++;
        }
        return new MaterialListPayload.Summary(m.material().name(), m.forms().size(), providers, duplicates, unified);
    }
}
