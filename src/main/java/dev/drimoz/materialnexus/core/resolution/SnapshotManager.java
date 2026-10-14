package dev.drimoz.materialnexus.core.resolution;

import java.util.Objects;
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
        ACTIVE.set(Objects.requireNonNull(snapshot, "snapshot"));
    }
}
