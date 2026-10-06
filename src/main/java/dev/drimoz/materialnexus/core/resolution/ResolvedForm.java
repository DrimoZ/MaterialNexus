package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.discovery.DiscoveryEvidence.Confidence;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Canonical choice for one material/form, with everything the GUI needs to answer "Why?".
 * {@code alternatives} are interchangeable duplicates; {@code notUnified} are providers that are
 * listed but deliberately left alone (variants, other materials, exclusions), each with its reason.
 * Reasons are translation keys plus arguments: every user-visible string lives in the lang files.
 */
public record ResolvedForm(
        Optional<ResourceLocation> canonical,
        List<ResourceLocation> alternatives,
        List<NotUnified> notUnified,
        PolicyPrecedence source,
        Confidence confidence,
        String reasonKey,
        List<String> reasonArgs,
        Optional<ResourceLocation> ignoredOverride) {
    public record NotUnified(ResourceLocation item, String reasonKey, List<String> reasonArgs) {
        public NotUnified {
            reasonArgs = List.copyOf(reasonArgs);
        }
    }

    public ResolvedForm {
        alternatives = List.copyOf(alternatives);
        notUnified = List.copyOf(notUnified);
        reasonArgs = List.copyOf(reasonArgs);
    }
}
