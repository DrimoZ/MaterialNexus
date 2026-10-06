package dev.drimoz.materialnexus.core.domain;

public record RecipeFamilyId(MaterialId material, FormId form) {
    @Override public String toString() { return material + "/" + form; }
}
