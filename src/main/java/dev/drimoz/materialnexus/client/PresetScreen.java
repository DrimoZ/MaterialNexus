package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.network.PreviewRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Lists the available presets (MNX-019); choosing one opens the Preview of the policy with it applied. */
public final class PresetScreen extends Screen {
    private final NexusScreen parent;
    private final List<ResourceLocation> presets;

    PresetScreen(NexusScreen parent, List<ResourceLocation> presets) {
        super(Component.translatable("screen.materialnexus.presets"));
        this.parent = parent;
        this.presets = presets;
    }

    NexusScreen parent() { return parent; }

    @Override
    protected void init() {
        int y = 40;
        for (ResourceLocation id : presets) {
            Component name = Component.translatableWithFallback("materialnexus.preset." + id.getPath(), id.getPath());
            Button b = Button.builder(name, x -> PacketDistributor.sendToServer(
                    new PreviewRequest(PendingChanges.all(), false, Optional.of(id), PendingChanges.processes(), PendingChanges.creations(), PendingChanges.data()))).bounds(width / 2 - 100, y, 200, 20).build();
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatableWithFallback("materialnexus.preset." + id.getPath() + ".desc", id.toString())));
            addRenderableWidget(b);
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.materialnexus.presets_intro"), width / 2, 24, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
