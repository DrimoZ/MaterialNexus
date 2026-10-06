package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.OpenNexusPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Placeholder shell; the material list and detail screens arrive with MNX-021. */
public final class NexusScreen extends Screen {
    private final OpenNexusPayload data;

    public NexusScreen(OpenNexusPayload data) {
        super(Component.translatable("screen.materialnexus.title"));
        this.data = data;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.materials", data.materialCount()), width / 2, 40, 0xAAAAAA);
        if (data.readOnly()) {
            graphics.drawCenteredString(font, Component.translatable("screen.materialnexus.read_only"), width / 2, 55, 0xFFAA00);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
