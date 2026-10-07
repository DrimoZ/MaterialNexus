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
    /** Large enough that the redesigned list (MNX-049) scrolls instead of paging; still a bound on untrusted requests. */
    public static final int PAGE_SIZE = 512;
    public static final int ALL = 0;
    public static final int TO_DECIDE = 1;
    public static final int UNIFIED = 2;
    public static final int MAX_QUERY = 64;

    private NexusQueries() { }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query) {
        return listPage(snapshot, page, query, false);
    }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query, boolean byForm) {
        return listPage(snapshot, page, query, byForm, ALL);
    }

    public static MaterialListPayload listPage(ResolvedSnapshot snapshot, int page, String query, boolean byForm, int status) {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<MaterialListPayload.Summary> matches = groups(snapshot, byForm).entrySet().stream()
                .filter(e -> e.getKey().contains(filter))
                .map(e -> summarize(e.getKey(), e.getValue()))
                .filter(s -> status == ALL || (status == TO_DECIDE ? s.toDecide() > 0 : s.unifiedForms() > 0))
                .toList();
        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clamped = net.minecraft.util.Mth.clamp(page, 0, pageCount - 1);
        int from = clamped * PAGE_SIZE;
        return new MaterialListPayload(clamped, pageCount, matches.size(), filter, byForm,
                matches.subList(from, Math.min(from + PAGE_SIZE, matches.size())), totals(snapshot));
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
                .toList(), List.of(), Map.of()));
    }

    /**
     * The whole pack as a grid (MNX-049): materials × forms, each cell {@code 0} absent, {@code 1} nothing to decide,
     * {@code 2} to decide (a suggestion), {@code 3} unified.
     */
    public static MatrixPayload matrix(ResolvedSnapshot snapshot) {
        List<String> materials = snapshot.materials().keySet().stream().map(MaterialId::name).toList();
        TreeMap<String, Integer> formCounts = new TreeMap<>();
        snapshot.materials().values().forEach(m -> m.forms().keySet().forEach(f -> formCounts.merge(f.name(), 1, Integer::sum)));
        // Most common forms first: the left of the grid is where most materials have something.
        List<String> forms = formCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        byte[] cells = new byte[materials.size() * forms.size()];
        int row = 0;
        for (var m : snapshot.materials().values()) {
            for (int col = 0; col < forms.size(); col++) {
                ResolvedForm f = m.forms().get(new FormId(forms.get(col)));
                cells[row * forms.size() + col] = (byte) (f == null ? 0 : f.alternatives().isEmpty() ? 1 : f.source() == PolicyPrecedence.DEFAULT ? 2 : 3);
            }
            row++;
        }
        return new MatrixPayload(materials, forms, cells);
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

    /** Whole-pack counts for the top bar. */
    public static MaterialListPayload.Totals totals(ResolvedSnapshot snapshot) {
        int toDecide = 0, unified = 0, byPriority = 0, aside = 0;
        Map<String, Integer> mods = new TreeMap<>();
        for (var m : snapshot.materials().values()) {
            for (ResolvedForm f : m.forms().values()) {
                if (!f.alternatives().isEmpty()) {
                    f.canonical().ifPresent(c -> mods.merge(c.getNamespace(), 1, Integer::sum));
                    f.alternatives().forEach(a -> mods.merge(a.getNamespace(), 1, Integer::sum));
                    if (f.source() == PolicyPrecedence.DEFAULT) toDecide++;
                    else unified++;
                    if (f.source() == PolicyPrecedence.GLOBAL || f.source() == PolicyPrecedence.MATERIAL || f.source() == PolicyPrecedence.FORM) byPriority++;
                }
                aside += f.notUnified().size();
            }
        }
        List<String> byUse = mods.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        return new MaterialListPayload.Totals(toDecide, unified, byPriority, aside, byUse);
    }

    /** The item that stands for a group in lists: its ingot, gem, dust or block, else the first canonical item. */
    private static Optional<net.minecraft.resources.ResourceLocation> icon(Map<String, ResolvedForm> forms) {
        for (String preferred : List.of("ingot", "gem", "dust", "block")) {
            ResolvedForm f = forms.get(preferred);
            if (f != null && f.canonical().isPresent()) return f.canonical();
        }
        return forms.values().stream().flatMap(f -> f.canonical().stream()).findFirst();
    }

    private static MaterialListPayload.Summary summarize(String name, Map<String, ResolvedForm> group) {
        Collection<ResolvedForm> forms = group.values();
        int providers = 0;
        int duplicates = 0;
        int unified = 0;
        for (ResolvedForm form : forms) {
            providers += (form.canonical().isPresent() ? 1 : 0) + form.alternatives().size() + form.notUnified().size();
            if (!form.alternatives().isEmpty()) duplicates++;
            if (!form.alternatives().isEmpty() && form.source() != PolicyPrecedence.DEFAULT) unified++;
        }
        return new MaterialListPayload.Summary(name, forms.size(), providers, duplicates, unified, icon(group));
    }
}
