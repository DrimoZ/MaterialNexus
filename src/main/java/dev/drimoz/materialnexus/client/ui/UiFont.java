package dev.drimoz.materialnexus.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

/**
 * The font of the Material Nexus screen (MNX-103): the game's, or the bundled smooth one (IBM Plex Sans, OFL, declared
 * in assets/materialnexus/font/ui.json) when the player chose it. The smooth font is the game's own font lookup with
 * the default font swapped, so every draw and every width goes through it without a change at the call sites.
 */
public final class UiFont {
    private static final FontDescription SMOOTH = new FontDescription.Resource(Identifier.fromNamespaceAndPath("materialnexus", "ui"));
    private static Font smooth;

    private UiFont() { }

    public static Font current() {
        Font game = Minecraft.getInstance().font;
        if (!ScreenSettings.get().smoothFont()) return game;
        if (smooth == null) {
            try {
                // No public path to the game font's glyph lookup; the game is unobfuscated, so the name is stable.
                Font.Provider glyphs = ObfuscationReflectionHelper.getPrivateValue(Font.class, game, "provider");
                smooth = new Font(new Font.Provider() {
                    @Override public net.minecraft.client.gui.GlyphSource glyphs(FontDescription font) { return glyphs.glyphs(FontDescription.DEFAULT.equals(font) ? SMOOTH : font); }
                    @Override public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() { return glyphs.effect(); }
                });
            } catch (RuntimeException e) {
                // The game's font is always there.
                return game;
            }
        }
        return smooth;
    }
}
