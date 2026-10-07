package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import dev.drimoz.materialnexus.network.PreviewPayload;
import dev.drimoz.materialnexus.network.RecipeFamilyPayload;
import dev.drimoz.materialnexus.network.SuggestionsPayload;
import dev.drimoz.materialnexus.network.UnifiedItemsPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common packet handlers. Responses are dropped if the screen was closed. */
public final class ClientHooks {
    private ClientHooks() { }

    public static void openNexus(OpenNexusPayload payload) {
        // Pending choices survive a failed apply; they are only dropped once the server confirms it wrote them.
        if (payload.applied()) PendingChanges.clear();
        Minecraft.getInstance().setScreen(new NexusScreen(payload.readOnly(), payload.canRevert(), payload.presets()));
    }

    public static void onMaterialList(MaterialListPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.acceptList(payload);
    }

    public static void onMaterialDetail(MaterialDetailPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.acceptDetail(payload);
    }

    public static void onRecipeFamily(RecipeFamilyPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.acceptFamily(payload);
    }

    public static void onProcess(dev.drimoz.materialnexus.network.ProcessPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.acceptProcess(payload);
    }

    public static void onDataList(dev.drimoz.materialnexus.network.DataListPayload payload) {
        if (Minecraft.getInstance().screen instanceof DataScreen screen) screen.acceptList(payload);
    }

    public static void onDataRead(dev.drimoz.materialnexus.network.DataReadPayload payload) {
        if (Minecraft.getInstance().screen instanceof DataScreen screen) screen.acceptRead(payload);
    }

    public static void onMatrix(dev.drimoz.materialnexus.network.MatrixPayload payload) {
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.acceptMatrix(payload);
    }

    public static void onPreview(PreviewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NexusScreen screen) screen.showPreview(payload);
        else if (mc.screen instanceof PresetScreen presets) mc.setScreen(new PreviewScreen(presets.parent(), payload));
        else if (mc.screen instanceof DataScreen data) mc.setScreen(new PreviewScreen(data, payload));
    }

    public static void onUnifiedItems(UnifiedItemsPayload payload) {
        UnifiedItemsClient.update(new java.util.HashSet<>(payload.alternatives()));
    }

    /** Adds every suggestion as a pending choice, keeping choices the player already made. */
    public static void onSuggestions(SuggestionsPayload payload) {
        for (CanonicalChange c : payload.changes()) {
            if (PendingChanges.get(c.material(), c.form()).isEmpty()) PendingChanges.set(c.material(), c.form(), c.provider());
        }
        if (Minecraft.getInstance().screen instanceof NexusScreen screen) screen.refresh();
    }
}
