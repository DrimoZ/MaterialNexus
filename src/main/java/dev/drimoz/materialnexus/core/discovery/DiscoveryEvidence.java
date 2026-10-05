package dev.drimoz.materialnexus.core.discovery;

public record DiscoveryEvidence(Source source, double confidence, String explanation) {
    public enum Source {
        EXPLICIT_DEFINITION,
        MATERIAL_TAG,
        INTEGRATION,
        CONSERVATIVE_HEURISTIC
    }
}
