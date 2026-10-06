package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import dev.drimoz.materialnexus.network.MaterialListPayload;
import dev.drimoz.materialnexus.network.MaterialListRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Placeholder shell; the material list and detail screens arrive with MNX-021. */
public final class NexusScreen extends Screen {
    private final boolean readOnly;
    private MaterialListPayload list;

    public NexusScreen(boolean readOnly) {
        super(Component.translatable("screen.materialnexus.title"));
        this.readOnly = readOnly;
    }

    @Override
    protected void init() {
        if (list == null) PacketDistributor.sendToServer(new MaterialListRequest(0, ""));
    }

    void onMaterialList(MaterialListPayload payload) { this.list = payload; }

    void onMaterialDetail(MaterialDetailPayload payload) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        if (list != null) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.materials", list.totalMatches()), width / 2, 40, 0xAAAAAA);
        }
        if (readOnly) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.read_only"), width / 2, 55, 0xFFAA00);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
