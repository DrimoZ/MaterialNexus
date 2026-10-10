package dev.drimoz.materialnexus.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * The multi-line editor of the Material Nexus screen (MNX-100): the vanilla box without its sprite, on a flat fill with
 * a control outline that turns to the focus colour. The vanilla text is always light, so the ground stays dark in every
 * theme, like a code editor.
 */
public final class Editor extends MultiLineEditBox {
    private static final TypeAdapter<JsonElement> STRICT = new Gson().getAdapter(JsonElement.class);

    public Editor(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height, Component.empty(), Component.empty());
    }

    @Override
    protected void renderBorder(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, Theme.GRAPHITE.bg);
        g.renderOutline(x, y, width, height, isFocused() ? Theme.INFO : Theme.CTRL);
    }

    /** What is wrong with {@code text} as JSON ("... at line 9 column 5"), or empty when it reads. */
    public static Optional<String> jsonError(String text) {
        try {
            STRICT.fromJson(text);
            return Optional.empty();
        } catch (IOException | JsonParseException e) {
            return Optional.of(String.valueOf(e.getMessage()));
        }
    }
}
