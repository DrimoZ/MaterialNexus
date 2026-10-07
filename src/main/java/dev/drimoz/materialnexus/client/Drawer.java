package dev.drimoz.materialnexus.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A side panel of the main screen: Preview (MNX-049) or pending changes (MNX-058). One at a time. */
interface Drawer {
    void layout(int x, int y, int width, int height);

    boolean contains(double mx, double my);

    void scroll(double delta);

    boolean click(double mx, double my, int button);

    void render(GuiGraphics g, Font font, int mx, int my);
}
