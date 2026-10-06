package dev.drimoz.materialnexus.core.domain;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence;
import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

public record Provider(
        ResourceLocation resource,
        MaterialId material,
        FormId form,
        List<DiscoveryEvidence> evidence) {
    public Provider {
        if (evidence.isEmpty()) throw new IllegalArgumentException("A provider needs at least one piece of evidence: " + resource);
        evidence = List.copyOf(evidence);
    }

    /** The resource namespace, i.e. the mod that registered it. */
    public String sourceMod() { return resource.getNamespace(); }

    /** Strongest confidence among the evidence. */
    public Confidence confidence() {
        return evidence.stream().map(DiscoveryEvidence::confidence).min(Comparator.naturalOrder()).orElseThrow();
    }
}
