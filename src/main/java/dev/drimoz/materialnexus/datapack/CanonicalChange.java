package dev.drimoz.materialnexus.datapack;

import net.minecraft.resources.ResourceLocation;

/** A requested canonical choice, as sent by the GUI. Untrusted until {@link PolicyEditor#preview} validated it. */
public record CanonicalChange(String material, String form, ResourceLocation provider) {
    /** MNX-058: as the provider, "back to default": the saved choice of this form is removed. */
    public static final ResourceLocation RESET = ResourceLocation.fromNamespaceAndPath("materialnexus", "default");

    public boolean reset() {
        return provider.equals(RESET);
    }
}
