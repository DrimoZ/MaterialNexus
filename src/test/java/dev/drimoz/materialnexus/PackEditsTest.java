package dev.drimoz.materialnexus;

import com.google.gson.JsonParser;
import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PackEdits;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MNX-118: a datapack's tag file is read as what it puts in and takes out, and Preview warns where Apply goes against it. */
class PackEditsTest {
    private static final ResourceLocation TIN = ResourceLocation.fromNamespaceAndPath("c", "ingots/tin");
    private static final ResourceLocation MEK = ResourceLocation.fromNamespaceAndPath("mekanism", "ingot_tin");
    private static final ResourceLocation MI = ResourceLocation.fromNamespaceAndPath("modern_industrialization", "tin_ingot");
    private static final ResourceLocation IE = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "ingot_tin");

    @Test
    void tagEditsAreReadAndOppositeChangesWarned() {
        var edit = PackEdits.tagEdit("file/my_tweaks", JsonParser.parseString(
                "{\"replace\": true, \"values\": [\"mekanism:ingot_tin\", {\"id\": \"modern_industrialization:tin_ingot\", \"required\": false}, \"#c:ingots\"],"
                        + " \"remove\": [\"immersiveengineering:ingot_tin\"]}").getAsJsonObject());
        assertEquals(new PackEdits.TagEdit("file/my_tweaks", true, List.of(MEK, MI), List.of(IE)), edit);

        // Apply takes MI out (the datapack puts it in) and puts IE back (the datapack takes it out); Mekanism is left alone.
        var effects = List.of(new PackContent.Effect(PackContent.TAG_REMOVE, TIN, MI), new PackContent.Effect(PackContent.TAG_ADD, TIN, IE),
                new PackContent.Effect(PackContent.ITEM_CONVERSION, MEK, MI));
        assertEquals(List.of(new PackContent.Effect(PackEdits.PACK_CONFLICT, MI, TIN), new PackContent.Effect(PackEdits.PACK_CONFLICT, IE, TIN)),
                PackEdits.conflicts(effects, Map.of(TIN, List.of(edit))));
    }
}
