package dev.drimoz.materialnexus.core.discovery;

/** One reason a resource was matched to a material/form. Its confidence is the source's rank. */
public record DiscoveryEvidence(Confidence confidence, String explanation) {
    /** Declared strongest first (docs/02-DOMAIN-MODEL.md); a lower ordinal wins. */
    public enum Confidence {
        EXPLICIT_DEFINITION,
        MATERIAL_TAG,
        INTEGRATION,
        CONSERVATIVE_HEURISTIC;

        public boolean isStrongerThan(Confidence other) { return ordinal() < other.ordinal(); }
    }
}
