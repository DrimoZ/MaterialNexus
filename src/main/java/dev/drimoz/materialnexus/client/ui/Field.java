package dev.drimoz.materialnexus.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The one text field of the Material Nexus screen (MNX-094): a vanilla {@link EditBox} without its sprite, on a flat
 * fill with a control outline that turns to the focus colour. Same constructor as {@link EditBox}.
 */
public final class Field extends EditBox {
    private static final int PAD = 4;

    public Field(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
        setTextColor(Theme.TEXT);
        setTextColorUneditable(Theme.FAINT);
    }

    @Override
    public void setHint(Component hint) {
        super.setHint(hint.copy().withColor(Theme.FAINT & 0xFFFFFF));
    }

    @Override
    public int getInnerWidth() { return width - 2 * PAD; }

    /** The text is drawn {@link #PAD} to the right of where an unbordered box puts it. */
    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - PAD, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        g.fill(getX(), getY(), getX() + width, getY() + height, Theme.RAISED);
        g.renderOutline(getX(), getY(), width, height, isFocused() ? Theme.INFO : Theme.CTRL);
        g.pose().pushPose();
        g.pose().translate(PAD, (height - 8) / 2, 0);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }
}
