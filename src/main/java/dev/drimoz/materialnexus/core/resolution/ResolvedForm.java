package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Canonical choice for one material/form, with everything the GUI needs to answer "Why?".
 * The reason is a translation key plus arguments: every user-visible string lives in the lang files.
 */
public record ResolvedForm(
        ResourceLocation canonical,
        List<ResourceLocation> alternatives,
        PolicyPrecedence source,
        Confidence confidence,
        String reasonKey,
        List<String> reasonArgs,
        Optional<ResourceLocation> ignoredOverride) {
    public ResolvedForm {
        alternatives = List.copyOf(alternatives);
        reasonArgs = List.copyOf(reasonArgs);
    }
}
