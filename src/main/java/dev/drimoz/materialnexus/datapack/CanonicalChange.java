package dev.drimoz.materialnexus.datapack;

import net.minecraft.resources.Identifier;

/** A requested canonical choice, as sent by the GUI. Untrusted until {@link PolicyEditor#preview} validated it. */
public record CanonicalChange(String material, String form, Identifier provider) {
    /** MNX-058: as the provider, "back to default": the saved choice of this form is removed. */
    public static final Identifier RESET = Identifier.fromNamespaceAndPath("materialnexus", "default");

    public boolean reset() {
        return provider.equals(RESET);
    }
}
