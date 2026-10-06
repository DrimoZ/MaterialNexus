package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common packet handlers. Responses are dropped if the screen was closed. */
public final class ClientHooks {
    private ClientHooks() { }

    public static void openNexus(OpenNexusPayload payload) {
        Minecraft.getInstance().setScreen(new NexusScreen(payload.readOnly()));
    }

    public static void onMaterialList(MaterialListPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.onMaterialList(payload);
    }

    public static void onMaterialDetail(MaterialDetailPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.onMaterialDetail(payload);
    }
}
