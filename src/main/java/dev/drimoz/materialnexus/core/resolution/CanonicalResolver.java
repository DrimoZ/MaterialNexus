package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.domain.Provider;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.policy.ResolutionPolicy;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm.NotUnified;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks one canonical provider per material/form. Pure and deterministic: input order never matters.
 *
 * <p>Only interchangeable candidates are unified. A tag means "usable as", not "the same item", so
 * providers are first sorted out (ADR-014): several items of one mod are variants of each other; an
 * item also tagged as a more specific material belongs there; an item named as a variant (Remin's
 * yellow_amethyst tagged as amethyst) or after another material (vanilla quartz tagged as milky
 * quartz) is set aside when a plainly named one exists (MNX-047); excluded materials/forms are left
 * alone. Nothing is removed from any tag: those providers are listed, explained, and untouched.
 */
public final class CanonicalResolver {
    private static final String VANILLA = "minecraft";
    private static final String WHY = "materialnexus.why.";
    private static final String NOT_UNIFIED = "materialnexus.not_unified.";
    private static final Comparator<Provider> TIE_BREAK = Comparator
            .comparing(Provider::confidence)
            .thenComparing(Provider::resource);

    private CanonicalResolver() { }

    public static SortedMap<MaterialId, ResolvedMaterial> resolve(DiscoveredMaterials discovered, ResolutionPolicy policy) {
        Specificity specificity = new Specificity(discovered);
        java.util.Set<String> names = discovered.materials().keySet().stream().map(MaterialId::name).collect(Collectors.toSet());
        // The words of a material's vanilla items name it too: lazuli for lapis.
        Map<MaterialId, java.util.Set<String>> vanillaWords = new HashMap<>();
        discovered.materials().forEach((material, forms) -> forms.values().forEach(list -> list.stream()
                .filter(p -> p.resource().getNamespace().equals(VANILLA))
                .forEach(p -> vanillaWords.computeIfAbsent(material, m -> new java.util.HashSet<>()).addAll(Names.words(p.resource())))));
        SortedMap<MaterialId, ResolvedMaterial> result = new TreeMap<>();
        discovered.materials().forEach((material, forms) -> {
            SortedMap<FormId, ResolvedForm> resolved = new TreeMap<>();
            forms.forEach((form, providers) -> {
                if (!providers.isEmpty()) resolved.put(form, resolveForm(new MaterialForm(material, form), providers, policy, specificity, names,
                        vanillaWords.getOrDefault(material, java.util.Set.of())));
            });
            result.put(material, new ResolvedMaterial(material, Collections.unmodifiableSortedMap(resolved)));
        });
        return Collections.unmodifiableSortedMap(result);
    }

    private static ResolvedForm resolveForm(MaterialForm key, List<Provider> providers, ResolutionPolicy policy, Specificity specificity,
                                            java.util.Set<String> names, java.util.Set<String> vanillaWords) {
        ResourceLocation explicit = policy.explicitProviders().get(key);
        boolean excluded = policy.isExcluded(key);
        Map<ResourceLocation, Optional<MaterialId>> elsewhereOf = new HashMap<>();
        for (Provider p : providers) elsewhereOf.put(p.resource(), specificity.moreSpecific(p.resource(), key));
        // Items that belong to another material do not make their mod look like it has several variants here.
        Map<String, Long> perMod = providers.stream()
                .filter(p -> elsewhereOf.get(p.resource()).isEmpty())
                .collect(Collectors.groupingBy(Provider::sourceMod, Collectors.counting()));

        List<Provider> candidates = new ArrayList<>();
        Map<ResourceLocation, NotUnified> notUnified = new TreeMap<>();
        for (Provider p : providers) {
            Optional<MaterialId> elsewhere = elsewhereOf.get(p.resource());
            if (excluded) {
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + "policy", List.of()));
            } else if (policy.tagRemoved(key, p.resource())) {
                // MNX-079: the player took it out of this form's tag; kept in sight so it can be put back.
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + "tag_removed", List.of()));
            } else if (policy.notSame().contains(p.resource())) {
                // MNX-050: the pack author said so; stronger than any guess.
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + "not_same", List.of()));
            } else if (elsewhere.isPresent()) {
                // Checked first: "belongs to steel" explains an umbrella tag better than "several items of one mod".
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + "more_specific", List.of(elsewhere.get().name())));
            } else if (perMod.get(p.sourceMod()) > 1) {
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + "same_mod", List.of(p.sourceMod())));
            } else {
                candidates.add(p);
            }
        }

        // MNX-047: tags say "usable as", names say what the item is. Only when a plainly named candidate exists.
        Map<ResourceLocation, String[]> oddNames = new HashMap<>();
        for (Provider p : candidates) Names.odd(p.resource(), key, names, vanillaWords).ifPresent(why -> oddNames.put(p.resource(), why));
        if (!oddNames.isEmpty() && oddNames.size() < candidates.size()) {
            candidates.removeIf(p -> {
                String[] why = oddNames.get(p.resource());
                if (why == null) return false;
                notUnified.put(p.resource(), new NotUnified(p.resource(), NOT_UNIFIED + why[0], List.of(why[1])));
                return true;
            });
        }

        Confidence strongest = providers.stream().map(Provider::confidence).min(Comparator.naturalOrder()).orElseThrow();
        if (excluded) {
            return new ResolvedForm(Optional.empty(), List.of(), List.copyOf(notUnified.values()), PolicyPrecedence.GLOBAL,
                    strongest, WHY + "excluded", List.of(), Optional.ofNullable(explicit));
        }

        if (explicit != null) {
            for (Provider p : providers) {
                if (p.resource().equals(explicit)) {
                    // An explicit choice may pick any discovered provider, even one left out above.
                    notUnified.remove(explicit);
                    return result(p, candidates, notUnified, PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE, Optional.empty(), "explicit", key.toString());
                }
            }
        }
        // An override naming an undiscovered item is reported, never turned into a provider.
        Optional<ResourceLocation> ignored = Optional.ofNullable(explicit);

        if (candidates.isEmpty()) {
            return new ResolvedForm(Optional.empty(), List.of(), List.copyOf(notUnified.values()), PolicyPrecedence.DEFAULT,
                    strongest, WHY + "nothing_to_unify", List.of(), ignored);
        }

        ResolvedForm byPriority = firstByModPriority(candidates, notUnified, policy.formModPriority().get(key), PolicyPrecedence.FORM, "form_priority", key.toString(), ignored);
        if (byPriority == null) byPriority = firstByModPriority(candidates, notUnified, policy.materialModPriority().get(key.material()), PolicyPrecedence.MATERIAL, "material_priority", key.material().toString(), ignored);
        if (byPriority == null) byPriority = firstByModPriority(candidates, notUnified, policy.globalModPriority(), PolicyPrecedence.GLOBAL, "global_priority", "", ignored);
        if (byPriority != null) return byPriority;

        if (candidates.size() == 1) return result(candidates.getFirst(), candidates, notUnified, PolicyPrecedence.DEFAULT, ignored, "only_provider");
        List<Provider> vanilla = candidates.stream().filter(p -> p.sourceMod().equals(VANILLA)).toList();
        if (!vanilla.isEmpty()) {
            return result(vanilla.stream().min(TIE_BREAK).orElseThrow(), candidates, notUnified, PolicyPrecedence.DEFAULT, ignored, "vanilla_default");
        }
        return result(candidates.stream().min(TIE_BREAK).orElseThrow(), candidates, notUnified, PolicyPrecedence.DEFAULT, ignored, "fallback");
    }

    private static ResolvedForm firstByModPriority(List<Provider> candidates, Map<ResourceLocation, NotUnified> notUnified, List<String> priority,
                                                   PolicyPrecedence source, String reason, String scope, Optional<ResourceLocation> ignored) {
        if (priority == null) return null;
        for (String mod : priority) {
            var match = candidates.stream().filter(p -> p.sourceMod().equals(mod)).min(TIE_BREAK);
            if (match.isPresent()) return result(match.get(), candidates, notUnified, source, ignored, reason, mod, scope);
        }
        return null;
    }

    private static ResolvedForm result(Provider canonical, List<Provider> candidates, Map<ResourceLocation, NotUnified> notUnified,
                                       PolicyPrecedence source, Optional<ResourceLocation> ignored, String reason, String... args) {
        List<ResourceLocation> alternatives = candidates.stream()
                .map(Provider::resource)
                .filter(r -> !r.equals(canonical.resource()))
                .sorted()
                .toList();
        return new ResolvedForm(Optional.of(canonical.resource()), alternatives, List.copyOf(notUnified.values()),
                source, canonical.confidence(), WHY + reason, List.of(args), ignored);
    }

    /** Reading item ids as words (MNX-047). */
    static final class Names {
        /** Words that name a form, not a material: what remains of an id without them should be the material. */
        private static final java.util.Set<String> FORM_WORDS = java.util.Set.of("ingot", "nugget", "gem", "dust", "plate", "sheet", "rod",
                "stick", "gear", "wire", "block", "storage", "ore", "raw", "deepslate", "nether", "end", "stone", "tiny", "small", "dirty",
                "clump", "shard", "crystal", "sheetmetal", "double", "large", "curved", "bolt", "ring", "blade", "rotor", "drill", "head",
                "fine", "item", "material", "chunk", "bar", "pile", "powder", "powdered", "grit");

        private Names() { }

        /**
         * Why an item does not look like plain {@code key}: {@code [reason, word]}, or empty. "names_other": once form
         * words are removed it names another known material (minecraft:quartz as milky quartz). "named_variant": a word
         * is left that is neither a form word nor the material (yellow_amethyst); vanilla ids are the reference names
         * and never count as variants. Words close to the material's (golden for gold, aluminium for aluminum) are fine.
         */
        static Optional<String[]> odd(ResourceLocation item, MaterialForm key, java.util.Set<String> materials, java.util.Set<String> vanillaWords) {
            List<String> words = words(item);
            String rest = String.join("_", words);
            if (!rest.isEmpty() && !rest.equals(key.material().name()) && materials.contains(rest)) return Optional.of(new String[] {"names_other", rest});
            if (item.getNamespace().equals(VANILLA)) return Optional.empty();
            List<String> own = new ArrayList<>(List.of(key.material().name().split("_")));
            own.addAll(vanillaWords);
            List<String> extra = words.stream().filter(w -> own.stream().noneMatch(o -> close(w, o))).toList();
            return extra.isEmpty() ? Optional.empty() : Optional.of(new String[] {"named_variant", String.join("_", extra)});
        }

        /** The words of an id that are not form words. */
        static List<String> words(ResourceLocation item) {
            List<String> words = new ArrayList<>();
            for (String w : item.getPath().split("[_/]")) {
                if (!w.isEmpty() && !isFormWord(w)) words.add(w);
            }
            return words;
        }

        private static boolean isFormWord(String w) {
            return FORM_WORDS.contains(w) || (w.endsWith("s") && FORM_WORDS.contains(w.substring(0, w.length() - 1)));
        }

        /** golden/gold, wooden/wood, aluminium/aluminum. */
        private static boolean close(String a, String b) {
            if (a.startsWith(b) || b.startsWith(a)) return true;
            int common = 0;
            while (common < Math.min(a.length(), b.length()) && a.charAt(common) == b.charAt(common)) common++;
            return common >= 5;
        }
    }

    /**
     * For each form, which materials each item is tagged as, and how many members each has. An item
     * in several materials of one form belongs to the one with the fewest members (the most
     * specific): stick_treated is a treated_wood rod before it is a wooden rod.
     */
    private static final class Specificity {
        private final Map<FormId, Map<ResourceLocation, List<MaterialId>>> materialsOf = new HashMap<>();
        private final Map<MaterialForm, Integer> size = new HashMap<>();

        Specificity(DiscoveredMaterials discovered) {
            discovered.materials().forEach((material, forms) -> forms.forEach((form, providers) -> {
                size.put(new MaterialForm(material, form), providers.size());
                var byItem = materialsOf.computeIfAbsent(form, f -> new HashMap<>());
                for (Provider p : providers) byItem.computeIfAbsent(p.resource(), r -> new ArrayList<>()).add(material);
            }));
        }

        Optional<MaterialId> moreSpecific(ResourceLocation item, MaterialForm key) {
            int own = size.get(key);
            Function<MaterialId, Integer> sizeOf = m -> size.get(new MaterialForm(m, key.form()));
            return materialsOf.getOrDefault(key.form(), Map.of()).getOrDefault(item, List.of()).stream()
                    .filter(m -> !m.equals(key.material()) && sizeOf.apply(m) < own)
                    .min(Comparator.comparing(sizeOf).thenComparing(Comparator.naturalOrder()));
        }
    }
}
