package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import dev.drimoz.materialnexus.network.PreviewPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common packet handlers. Responses are dropped if the screen was closed. */
public final class ClientHooks {
    private ClientHooks() { }

    public static void openNexus(OpenNexusPayload payload) {
        Minecraft.getInstance().setScreen(new MaterialListScreen(payload.readOnly(), payload.canRevert()));
    }

    public static void onMaterialList(MaterialListPayload payload) {
        if (Minecraft.getInstance().screen instanceof MaterialListScreen screen) screen.accept(payload);
    }

    public static void onMaterialDetail(MaterialDetailPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MaterialListScreen list) mc.setScreen(new MaterialDetailScreen(list, payload));
    }

    public static void onPreview(PreviewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MaterialListScreen list) mc.setScreen(new PreviewScreen(list, payload));
    }
}
