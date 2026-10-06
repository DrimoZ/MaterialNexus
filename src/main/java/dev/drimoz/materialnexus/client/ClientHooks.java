package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.OpenNexusPayload;
import dev.drimoz.materialnexus.network.PreviewPayload;
import dev.drimoz.materialnexus.network.SuggestionsPayload;
import dev.drimoz.materialnexus.network.UnifiedItemsPayload;
import net.minecraft.client.Minecraft;

/** Client-only entry points called from common packet handlers. Responses are dropped if the screen was closed. */
public final class ClientHooks {
    private ClientHooks() { }

    public static void openNexus(OpenNexusPayload payload) {
        // Pending choices survive a failed apply; they are only dropped once the server confirms it wrote them.
        if (payload.applied()) PendingChanges.clear();
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

    public static void onUnifiedItems(UnifiedItemsPayload payload) {
        UnifiedItemsClient.update(new java.util.HashSet<>(payload.alternatives()));
    }

    /** Adds every suggestion as a pending choice, keeping choices the player already made. */
    public static void onSuggestions(SuggestionsPayload payload) {
        for (CanonicalChange c : payload.changes()) {
            if (PendingChanges.get(c.material(), c.form()).isEmpty()) PendingChanges.set(c.material(), c.form(), c.provider());
        }
        if (Minecraft.getInstance().screen instanceof MaterialListScreen list) list.refresh();
    }
}
