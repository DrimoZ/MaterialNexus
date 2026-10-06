package dev.drimoz.materialnexus.core.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Semantic form key such as {@code ingot} or {@code plate}. Namespace-free for the same reason as
 * {@link MaterialId}: a plate is a plate whichever mod declares it.
 */
public record FormId(String name) implements Comparable<FormId> {
    public static final Codec<FormId> CODEC = Codec.STRING.comapFlatMap(FormId::read, FormId::name);

    public FormId {
        if (!isValidName(name)) throw new IllegalArgumentException("Invalid form name: " + name);
    }

    public static DataResult<FormId> read(String name) {
        return isValidName(name)
                ? DataResult.success(new FormId(name))
                : DataResult.error(() -> "Invalid form name (expected [a-z0-9_]+): " + name);
    }

    /** Shared rule for material and form names: non-empty {@code [a-z0-9_]+}. */
    static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_') return false;
        }
        return true;
    }

    @Override public int compareTo(FormId other) { return name.compareTo(other.name); }

    @Override public String toString() { return name; }
}
