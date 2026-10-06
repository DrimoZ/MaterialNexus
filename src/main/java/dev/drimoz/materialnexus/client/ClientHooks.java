package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.OpenNexusPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common packet handlers. */
public final class ClientHooks {
    private ClientHooks() { }

    public static void openNexus(OpenNexusPayload payload) {
        Minecraft.getInstance().setScreen(new NexusScreen(payload));
    }
}
