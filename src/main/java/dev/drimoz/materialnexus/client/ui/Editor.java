package dev.drimoz.materialnexus.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * The multi-line editor of the Material Nexus screen (MNX-100): the vanilla box without its sprite, on a flat fill with
 * a control outline that turns to the focus colour. The vanilla text is always light, so the ground stays dark in every
 * theme, like a code editor. MNX-136: the box can no longer be extended on 26.1, so the panel draws {@link #ground}
 * just before it.
 */
public final class Editor {
    private static final TypeAdapter<JsonElement> STRICT = new Gson().getAdapter(JsonElement.class);

    private Editor() { }

    public static MultiLineEditBox create(Font font, int x, int y, int width, int height) {
        return MultiLineEditBox.builder().setX(x).setY(y).setShowBackground(false).build(font, width, height, Component.empty());
    }

    public static void ground(GuiGraphicsExtractor g, MultiLineEditBox box) {
        g.fill(box.getX(), box.getY(), box.getRight(), box.getBottom(), Theme.GRAPHITE.bg);
        g.outline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), box.isFocused() ? Theme.INFO : Theme.CTRL);
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
