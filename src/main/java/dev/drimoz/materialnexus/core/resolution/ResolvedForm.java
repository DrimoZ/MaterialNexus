package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Canonical choice for one material/form, with everything the GUI needs to answer "Why?". */
public record ResolvedForm(
        ResourceLocation canonical,
        List<ResourceLocation> alternatives,
        PolicyPrecedence source,
        Confidence confidence,
        String reason) {
    public ResolvedForm {
        alternatives = List.copyOf(alternatives);
    }
}
