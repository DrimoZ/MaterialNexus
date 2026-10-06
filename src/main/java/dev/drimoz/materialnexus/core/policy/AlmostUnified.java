package dev.drimoz.materialnexus.core.policy;

import java.util.Map;

/**
 * Who unifies what when Almost Unified is installed (ADR-012). Without AU, Material Nexus owns every
 * domain. With AU, Material Nexus only acts in the domains the pack author explicitly gave it in
 * {@code global.json}; AU unifies by default, so an unarbitrated domain is left to AU rather than done twice.
 */
public record AlmostUnified(Map<Domain, Owner> owners) {
    public static final String MOD_ID = "almostunified";
    public static final AlmostUnified NONE = new AlmostUnified(Map.of());

    public enum Domain {
        TAGS("tags"), OUTPUT_REWRITE("output_rewrite"), RECIPE_DISABLE("recipe_disable"), VIEWER_HIDING("viewer_hiding");

        public final String key;

        Domain(String key) { this.key = key; }
    }

    public enum Owner { MNX, AU }

    /** What Material Nexus may generate, once AU presence is known. */
    public record Ownership(boolean tags, boolean outputRewrite, boolean recipeDisable, boolean viewerHiding) {
        public static final Ownership ALL = new Ownership(true, true, true, true);
    }

    public AlmostUnified {
        owners = Map.copyOf(owners);
    }

    public boolean mnxOwns(Domain domain, boolean auPresent) {
        return !auPresent || owners.get(domain) == Owner.MNX;
    }

    public Ownership ownership(boolean auPresent) {
        return new Ownership(mnxOwns(Domain.TAGS, auPresent), mnxOwns(Domain.OUTPUT_REWRITE, auPresent),
                mnxOwns(Domain.RECIPE_DISABLE, auPresent), mnxOwns(Domain.VIEWER_HIDING, auPresent));
    }
}
