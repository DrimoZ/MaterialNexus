package dev.drimoz.materialnexus.datapack;

import net.minecraft.resources.ResourceLocation;

/** A requested canonical choice, as sent by the GUI. Untrusted until {@link PolicyEditor#preview} validated it. */
public record CanonicalChange(String material, String form, ResourceLocation provider) { }
