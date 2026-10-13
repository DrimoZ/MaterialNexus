package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.MaterialNexus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Dev only (MNX-049): with {@code -Dmaterialnexus.uishots=true} (the {@code uiShots} run), once the world is loaded,
 * opens Material Nexus, walks its views and saves a screenshot of each to {@code screenshots/}, then quits.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID, value = Dist.CLIENT)
public final class UiShots {
    private static final boolean ENABLED = Boolean.getBoolean("materialnexus.uishots");
    private record Step(int tick, String view, String arg, String shot) { }

    private static final List<Step> STEPS = List.of(
            new Step(60, "open", "", ""),
            new Step(100, "priority", "", ""),
            new Step(110, "", "", "mnx_1_home"),
            new Step(115, "material", "copper", ""),
            // The material detail reads the recipes of the pack first: a few seconds on the dev pack.
            new Step(330, "", "", "mnx_2_material"),
            new Step(335, "matrix", "", ""),
            new Step(400, "", "", "mnx_3_matrix"),
            new Step(405, "form", "ingot", ""),
            new Step(560, "", "", "mnx_4_form"),
            new Step(562, "form", "rod", ""),
            new Step(700, "process", "", ""),
            new Step(800, "", "", "mnx_5_process"),
            new Step(801, "mouse", "180,296", ""),
            new Step(803, "", "", "mnx_17_route_hover"),
            new Step(804, "mouse", "", ""),
            new Step(805, "forms_tab", "", ""),
            new Step(810, "suggestions", "", ""),
            new Step(840, "preview", "", ""),
            new Step(1080, "", "", "mnx_6_preview"),
            new Step(1085, "triage", "", ""),
            new Step(1200, "", "", "mnx_7_triage"),
            new Step(1205, "home", "", ""),
            new Step(1208, "priority", "", ""),
            new Step(1212, "preview", "", ""),
            new Step(1330, "", "", "mnx_8_home_preview"),
            new Step(1335, "presets", "", ""),
            new Step(1360, "", "", "mnx_9_presets"),
            new Step(1362, "pending", "", ""),
            new Step(1375, "", "", "mnx_10_pending"),
            new Step(1378, "data", "", ""),
            new Step(1420, "", "", "mnx_11_data"),
            // MNX-076: only shown when scripts decide something (-Pkubejs with a script in run-ui/kubejs).
            new Step(1425, "scripts", "", ""),
            new Step(1440, "", "", "mnx_12_scripts"),
            new Step(1445, "settings", "", ""),
            new Step(1460, "", "", "mnx_13_settings"),
            new Step(1466, "material", "copper", ""),
            new Step(1560, "recipes", "ingot", ""),
            new Step(1660, "", "", "mnx_14_recipes"),
            new Step(1661, "mouse", "350,342", ""),
            new Step(1663, "", "", "mnx_18_recipe_hover"),
            new Step(1664, "mouse", "", ""),
            new Step(1666, "shortcuts", "", ""),
            new Step(1680, "", "", "mnx_15_shortcuts"),
            new Step(1686, "smooth", "", ""),
            new Step(1690, "material", "copper", ""),
            new Step(1790, "", "", "mnx_16_smooth"),
            new Step(1795, "data", "", ""),
            new Step(1830, "click", "208,51", ""),
            new Step(1870, "", "", "mnx_19_untagged"),
            new Step(1875, "quit", "", ""));
    private static int ticks;
    private static int next;

    private UiShots() { }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED || next >= STEPS.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        Step step = STEPS.get(next);
        if (ticks < step.tick()) return;
        next++;
        if (!step.shot().isEmpty()) {
            Screenshot.grab(mc.gameDirectory, step.shot() + ".png", mc.getMainRenderTarget(), message -> { });
            return;
        }
        switch (step.view()) {
            case "open" -> {
                // Through the server, as the command does, so presets and global.json are the real ones.
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                server.execute(() -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        dev.drimoz.materialnexus.network.OpenNexusPayload.forPlayer(player)));
            }
            case "quit" -> mc.stop();
            default -> { if (mc.screen instanceof NexusScreen screen) screen.devShow(step.view(), step.arg()); }
        }
    }
}
