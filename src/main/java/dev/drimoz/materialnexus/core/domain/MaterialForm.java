package dev.drimoz.materialnexus.core.domain;

/** A material in one form, e.g. {@code copper/plate}. Key for canonical resolution and recipe families. */
public record MaterialForm(MaterialId material, FormId form) {
    @Override public String toString() { return material + "/" + form; }
}
