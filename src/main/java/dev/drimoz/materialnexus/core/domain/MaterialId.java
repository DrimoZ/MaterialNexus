package dev.drimoz.materialnexus.core.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Semantic material key such as {@code copper} or {@code rose_gold}. Deliberately namespace-free:
 * every mod's copper is the same material, matching the {@code c:ingots/copper} tag convention.
 */
public record MaterialId(String name) implements Comparable<MaterialId> {
    public static final Codec<MaterialId> CODEC = Codec.STRING.comapFlatMap(MaterialId::read, MaterialId::name);

    public MaterialId {
        if (!FormId.isValidName(name)) throw new IllegalArgumentException("Invalid material name: " + name);
    }

    public static DataResult<MaterialId> read(String name) {
        return FormId.isValidName(name)
                ? DataResult.success(new MaterialId(name))
                : DataResult.error(() -> "Invalid material name (expected [a-z0-9_]+): " + name);
    }

    @Override public int compareTo(MaterialId other) { return name.compareTo(other.name); }

    @Override public String toString() { return name; }
}
