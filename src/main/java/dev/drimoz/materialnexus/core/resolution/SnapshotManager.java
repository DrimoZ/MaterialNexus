package dev.drimoz.materialnexus.core.resolution;

import java.util.concurrent.atomic.AtomicReference;

/** Owns the immutable server-side resolved state. */
public final class SnapshotManager {
    private static final AtomicReference<ResolvedSnapshot> ACTIVE =
            new AtomicReference<>(ResolvedSnapshot.empty());

    private SnapshotManager() {}


    public static ResolvedSnapshot current() {
        return ACTIVE.get();
    }

    public static void swap(ResolvedSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot cannot be null");
        ACTIVE.set(snapshot);
    }
}
