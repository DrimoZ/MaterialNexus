package dev.drimoz.materialnexus.network;

import net.minecraft.resources.ResourceLocation;

/**
 * Forge 1.20.1 port: the shape of 1.21's payload interface, so every payload record stays as on main.
 * {@link MnxNetwork} registers them on one {@code SimpleChannel}; the type id names the message in logs only.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) { }
}
