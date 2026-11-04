package dev.drimoz.materialnexus.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        super.onClick(new net.minecraft.client.input.MouseButtonEvent(event.x() - PAD, event.y(), event.buttonInfo()), doubleClick);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        g.fill(getX(), getY(), getX() + width, getY() + height, Theme.RAISED);
        g.outline(getX(), getY(), width, height, isFocused() ? Theme.INFO : Theme.CTRL);
        g.pose().pushMatrix();
        g.pose().translate(PAD, (height - 8) / 2);
        super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        g.pose().popMatrix();
    }
}
