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

    // MNX-139, -Pflow=fresh: a world made in 26.1 with the real mods, choices taken and applied; -Pflow=after: the same world
    // after a restart.
    private static final String FLOW = System.getProperty("materialnexus.uishots.flow", "");
    private static final List<Step> FRESH = List.of(
            new Step(60, "open", "", ""),
            new Step(160, "priority", "", ""),
            new Step(220, "suggestions", "", ""),
            new Step(320, "", "", "flow_1_home_chosen"),
            new Step(325, "preview", "", ""),
            new Step(620, "", "", "flow_2_preview"),
            new Step(625, "apply", "", ""),
            new Step(1500, "", "", "flow_3_home_applied"),
            new Step(1505, "material", "silver", ""),
            new Step(1650, "", "", "flow_4_silver"),
            new Step(1655, "material", "copper", ""),
            new Step(1800, "", "", "flow_5_copper"),
            new Step(1805, "quit", "", ""));
    private static final List<Step> AFTER = List.of(
            new Step(60, "open", "", ""),
            new Step(160, "", "", "flow_6_home_restarted"),
            new Step(165, "material", "silver", ""),
            new Step(300, "", "", "flow_7_silver_restarted"),
            new Step(305, "click", "295,95", ""),
            new Step(340, "", "", "flow_8_silver_forms"),
            new Step(345, "quit", "", ""));
    // MNX-143, -Pflow=more on the same world: the copper dust recipes after Apply, the JSON editor, Revert, then Restore.
    private static final List<Step> MORE = List.of(
            new Step(60, "open", "", ""),
            new Step(100, "material", "copper", ""),
            new Step(240, "recipes", "dust", ""),
            new Step(400, "", "", "flow_9_copper_dust_recipes"),
            new Step(405, "data", "", ""),
            new Step(460, "click", "153,125", ""),
            new Step(560, "", "", "flow_10_editor"),
            new Step(565, "home", "", ""),
            new Step(600, "revert", "", ""),
            new Step(1300, "open", "", ""),
            new Step(1400, "", "", "flow_11_reverted"),
            new Step(1405, "restore", "", ""),
            new Step(2100, "open", "", ""),
            new Step(2200, "", "", "flow_12_restored"),
            new Step(2205, "quit", "", ""));
    // MNX-144, uiShotsMp on a dedicated server: an operator sees it read-only and is refused Apply; a guest has no command
    // and a raw Apply of theirs is ignored.
    private static final List<Step> MP_OP = List.of(
            new Step(100, "open", "", ""),
            new Step(260, "", "", "mp_1_op_open"),
            new Step(265, "material", "copper", ""),
            new Step(420, "", "", "mp_2_op_copper"),
            new Step(425, "pending", "", ""),
            new Step(430, "apply", "", ""),
            new Step(470, "close", "", ""),
            new Step(480, "", "", "mp_3_op_apply_refused"),
            new Step(485, "quit", "", ""));
    private static final List<Step> MP_GUEST = List.of(
            new Step(100, "open", "", ""),
            new Step(120, "", "", "mp_4_guest_command"),
            new Step(125, "rawapply", "", ""),
            new Step(200, "", "", "mp_5_guest_raw_apply"),
            new Step(205, "quit", "", ""));
    // MNX-144, -Pflow=views on the same world: the views the journey does not cross.
    private static final List<Step> VIEWS = List.of(
            new Step(60, "open", "", ""),
            new Step(100, "material", "copper", ""),
            new Step(240, "missing", "", ""),
            new Step(300, "", "", "views_1_missing"),
            new Step(305, "form", "dust", ""),
            new Step(450, "process", "", ""),
            new Step(600, "", "", "views_2_process"),
            new Step(605, "triage", "", ""),
            new Step(760, "", "", "views_3_triage"),
            new Step(765, "presets", "", ""),
            new Step(820, "", "", "views_4_presets"),
            new Step(825, "scripts", "", ""),
            new Step(880, "", "", "views_5_scripts"),
            new Step(885, "settings", "", ""),
            new Step(920, "", "", "views_6_settings"),
            new Step(925, "quit", "", ""));
    private static final List<Step> STEPS = FLOW.equals("fresh") ? FRESH : FLOW.equals("after") ? AFTER : FLOW.equals("more") ? MORE : FLOW.equals("mpop") ? MP_OP : FLOW.equals("mpguest") ? MP_GUEST : FLOW.equals("views") ? VIEWS : List.of(
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
            // MNX-117: the bin of the first row, then "Put this recipe back" in the inspector, so later captures count the same.
            // Frames are slow on the dev pack: the pointer is drawn (its click areas made) before clicking.
            new Step(1665, "mouse", "722,148", ""),
            new Step(1700, "click", "722,148", ""),
            new Step(1760, "", "", "mnx_20_recipe_remove"),
            new Step(1765, "click", "925,490", ""),
            new Step(1766, "mouse", "", ""),
            new Step(1770, "shortcuts", "", ""),
            new Step(1784, "", "", "mnx_15_shortcuts"),
            new Step(1790, "smooth", "", ""),
            new Step(1794, "material", "copper", ""),
            new Step(1894, "", "", "mnx_16_smooth"),
            new Step(1899, "data", "", ""),
            new Step(1934, "click", "208,51", ""),
            new Step(1974, "", "", "mnx_19_untagged"),
            new Step(1979, "quit", "", ""));
    private static int ticks;
    private static int waited;
    private static boolean created;
    private static int next;

    private UiShots() { }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED || next >= STEPS.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (FLOW.equals("fresh") && mc.level == null && !created && mc.screen != null && ++waited > 40) {
            // Quick play only opens a world that exists: a world made now, as Create New World would (normal preset, creative).
            created = true;
            mc.createWorldOpenFlows().createFreshLevel("New World", new net.minecraft.world.level.LevelSettings("New World",
                            net.minecraft.world.level.GameType.CREATIVE, net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    net.minecraft.world.level.levelgen.WorldOptions.defaultWithRandomSeed(),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, mc.screen);
            return;
        }
        // MNX-139: a world being made shows its loading screen for a while; the steps count from when it is gone.
        if (mc.player == null || mc.level == null || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) return;
        ticks++;
        Step step = STEPS.get(next);
        if (ticks < step.tick()) return;
        next++;
        if (!step.shot().isEmpty()) {
            Screenshot.grab(mc.gameDirectory, step.shot() + ".png", mc.getMainRenderTarget(), 1, message -> { });
            return;
        }
        switch (step.view()) {
            // MNX-144: on a dedicated server, the way a player opens it.
            case "open" -> {
                if (mc.getSingleplayerServer() == null) { mc.player.connection.sendCommand("materials"); return; }
                // Through the server, as the command does, so presets and global.json are the real ones.
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                server.execute(() -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        dev.drimoz.materialnexus.network.OpenNexusPayload.forPlayer(player)));
            }
            case "quit" -> mc.stop();
            case "close" -> mc.setScreen(null);
            // An Apply sent without the screen, as a modified client could: the server must ignore it for a guest.
            case "rawapply" -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new dev.drimoz.materialnexus.network.PreviewRequest(java.util.List.of(), true, java.util.Optional.empty(), java.util.Map.of(),
                            java.util.List.of(), java.util.Map.of(), java.util.Optional.of("{\"mod_priority\":[\"cyclic\"]}")));
            default -> { if (mc.screen instanceof NexusScreen screen) screen.devShow(step.view(), step.arg()); }
        }
    }
}
