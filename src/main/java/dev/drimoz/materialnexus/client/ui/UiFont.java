package dev.drimoz.materialnexus.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.util.function.Function;

/**
 * The font of the Material Nexus screen (MNX-103): the game's, or the bundled smooth one (IBM Plex Sans, OFL, declared
 * in assets/materialnexus/font/ui.json) when the player chose it. The smooth font is the game's own font lookup with
 * the default font swapped, so every draw and every width goes through it without a change at the call sites.
 */
public final class UiFont {
    private static final ResourceLocation SMOOTH = ResourceLocation.fromNamespaceAndPath("materialnexus", "ui");
    private static Font smooth;

    private UiFont() { }

    public static Font current() {
        Font game = Minecraft.getInstance().font;
        if (!ScreenSettings.get().smoothFont()) return game;
        if (smooth == null) {
            try {
                Function<ResourceLocation, FontSet> fonts = ObfuscationReflectionHelper.getPrivateValue(Font.class, game, "fonts");
                smooth = new Font(id -> fonts.apply(Minecraft.DEFAULT_FONT.equals(id) ? SMOOTH : id), false);
            } catch (RuntimeException e) {
                // The game's font is always there.
                return game;
            }
        }
        return smooth;
    }
}
