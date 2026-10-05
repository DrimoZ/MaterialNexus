package dev.drimoz.materialnexus.core.domain;

import java.util.List;

public record MaterialDefinition(
        MaterialId id,
        List<FormId> forms,
        List<String> aliases) {
    public MaterialDefinition {
        forms = List.copyOf(forms);
        aliases = List.copyOf(aliases);
    }
}
