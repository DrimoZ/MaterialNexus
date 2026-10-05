package dev.drimoz.materialnexus.core.domain;

import net.minecraft.resources.ResourceLocation;

public record FormId(ResourceLocation id) {
    public static FormId of(String path) {
        return new FormId(ResourceLocation.fromNamespaceAndPath("materialnexus", path));
    }
}
