package dev.drimoz.materialnexus.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The one button of the Material Nexus screen (MNX-094): a vanilla {@link Button} (focus, keyboard, narration) drawn
 * flat in the theme's roles. One {@link Kind#PRIMARY} per view; {@link Kind#DANGER} for what cannot be taken back.
 */
public final class Btn extends Button {
    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER }

    private final Kind kind;

    private Btn(int x, int y, int width, int height, Component label, Kind kind, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.kind = kind;
    }

    /** Same shape as {@link Button#builder}: {@code Btn.of(label, b -> ...).bounds(x, y, w, h).build()}. */
    public static Spec of(Component label, OnPress onPress) { return new Spec(label, onPress); }

    public static final class Spec {
        private final Component label;
        private final OnPress onPress;
        private Kind kind = Kind.SECONDARY;
        private int x, y, width = 150, height = Theme.CONTROL;

        private Spec(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Spec kind(Kind kind) { this.kind = kind; return this; }

        public Spec bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }

        public Btn build() { return new Btn(x, y, width, height, label, kind, onPress); }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean over = active && isHoveredOrFocused();
        int fill = switch (kind) {
            case PRIMARY -> !active ? Theme.RAISED : over ? Theme.mix(Theme.ACCENT, Theme.TEXT, 0.18f) : Theme.ACCENT;
            case SECONDARY -> over ? Theme.SEL : Theme.RAISED;
            case GHOST, DANGER -> over ? Theme.RAISED : 0;
        };
        int text = !active ? Theme.FAINT : switch (kind) {
            case PRIMARY -> Theme.ON;
            case SECONDARY -> Theme.TEXT;
            case GHOST -> over ? Theme.TEXT : Theme.MUTED;
            case DANGER -> Theme.BAD;
        };
        if (fill != 0) g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        if (isFocused()) g.renderOutline(getX(), getY(), width, height, Theme.INFO);
        var font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2, getY() + (height - 8) / 2, text, false);
    }
}
