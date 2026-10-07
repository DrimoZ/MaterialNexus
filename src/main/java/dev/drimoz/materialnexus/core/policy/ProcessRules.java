package dev.drimoz.materialnexus.core.policy;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialForm;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Process rules (MNX-036): how a form is made, e.g. "every rod: IE metal press 1 ingot → 2 rods". Per form for
 * every material, with per-material overrides (an override with no route switches the rule off for that
 * material). Balance-affecting, so only ever generated from an explicit policy and shown in Preview (ADR-003).
 */
public record ProcessRules(Map<FormId, Rule> forms, Map<MaterialForm, Rule> overrides) {
    public static final ProcessRules NONE = new ProcessRules(Map.of(), Map.of());

    /** {@code machine} is a recipe type; {@code in} items of the {@code input} form give {@code out} items. */
    public record Route(ResourceLocation machine, FormId input, int in, int out) {
        public Route {
            if (in < 1 || out < 1 || in > 64 || out > 64) throw new IllegalArgumentException("Process ratio out of range 1..64: " + in + " -> " + out);
        }
    }

    /**
     * {@code exclusive}: recipes making this form any other way are disabled. {@code enforceRatio}: an existing
     * recipe of a route machine with another ratio is replaced; otherwise only missing routes are generated.
     */
    public record Rule(List<Route> routes, boolean exclusive, boolean enforceRatio) {
        public Rule {
            routes = List.copyOf(routes);
        }
    }

    public ProcessRules {
        forms = Map.copyOf(forms);
        overrides = Map.copyOf(overrides);
    }

    public Optional<Rule> ruleFor(MaterialForm key) {
        Rule rule = overrides.containsKey(key) ? overrides.get(key) : forms.get(key.form());
        return Optional.ofNullable(rule).filter(r -> !r.routes().isEmpty());
    }

    /** These form-level rules replace the current ones (an empty rule removes it): pending GUI edits, before they are written. */
    public ProcessRules withForms(Map<FormId, Rule> edits) {
        Map<FormId, Rule> merged = new java.util.HashMap<>(forms);
        edits.forEach((form, rule) -> {
            if (rule.routes().isEmpty() && !rule.exclusive() && !rule.enforceRatio()) merged.remove(form);
            else merged.put(form, rule);
        });
        return new ProcessRules(merged, overrides);
    }

    public boolean isEmpty() {
        return forms.isEmpty() && overrides.isEmpty();
    }
}
