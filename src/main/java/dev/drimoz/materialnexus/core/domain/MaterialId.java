package dev.drimoz.materialnexus.core.domain;

import net.minecraft.resources.ResourceLocation;

public record MaterialId(ResourceLocation id) {
    public MaterialId {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
    }

    @Override public String toString() { return id.toString(); }
}
