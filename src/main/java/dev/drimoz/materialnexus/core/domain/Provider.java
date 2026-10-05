package dev.drimoz.materialnexus.core.domain;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record Provider(
        ResourceLocation resource,
        MaterialId material,
        FormId form,
        String sourceMod,
        double confidence,
        List<String> evidence) {
    public Provider {
        evidence = List.copyOf(evidence);
    }
}
