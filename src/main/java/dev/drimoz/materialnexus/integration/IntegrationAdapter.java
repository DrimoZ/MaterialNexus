package dev.drimoz.materialnexus.integration;

/** Optional integration boundary. Implementations must be safe when their target mod is absent. */
public interface IntegrationAdapter {
    String id();
    default void contribute() { }
}
