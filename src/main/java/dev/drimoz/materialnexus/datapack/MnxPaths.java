package dev.drimoz.materialnexus.datapack;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** {@code config/materialnexus/}: shipped with the modpack, shared by every world (ADR-007/008). */
public final class MnxPaths {
    private MnxPaths() { }

    public static Path root() { return FMLPaths.CONFIGDIR.get().resolve("materialnexus"); }

    public static Path policies() { return root().resolve("policies"); }

    public static Path generated() { return root().resolve("generated"); }
}
