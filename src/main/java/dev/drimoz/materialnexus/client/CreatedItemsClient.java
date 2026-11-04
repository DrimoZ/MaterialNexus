package dev.drimoz.materialnexus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.item.CreatedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created items (MNX-039) are drawn with their form's grayscale template (models written in the created items pack),
 * tinted with the material colour (from {@code items.json}, else the average colour of the material's ingot texture).
 * Only layer 1, where the templates put their texture, is tinted (MNX-073): an item with its own texture, or whose
 * model a resource pack replaced, keeps its colours.
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID, value = Dist.CLIENT)
public final class CreatedItemsClient {
    private static final int FALLBACK = 0xA0A0A0;
    private static final Map<Identifier, Integer> COLORS = new HashMap<>();

    private CreatedItemsClient() { }

    /** MNX-137: on 26.1 the item definitions the created items pack writes name this tint for layer 1. */
    @SubscribeEvent
    public static void registerTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(MaterialNexus.MOD_ID, "material"), MaterialTint.CODEC);
    }

    /** The material colour of a created item; white on any other item. */
    public record MaterialTint() implements net.minecraft.client.color.item.ItemTintSource {
        static final com.mojang.serialization.MapCodec<MaterialTint> CODEC = com.mojang.serialization.MapCodec.unit(new MaterialTint());

        @Override
        public int calculate(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity owner) {
            return stack.getItem() instanceof CreatedItem created ? 0xFF000000 | color(created.entry()) : -1;
        }

        @Override
        public com.mojang.serialization.MapCodec<MaterialTint> type() { return CODEC; }
    }

    private static int color(dev.drimoz.materialnexus.item.CreatedItems.Entry entry) {
        if (entry.color().isPresent()) return entry.color().get();
        return entry.colorFrom().map(id -> COLORS.computeIfAbsent(id, CreatedItemsClient::averageColor)).orElse(FALLBACK);
    }

    /** Average of the opaque pixels of {@code <ns>:textures/item/<path>.png}, the usual place of an item texture. */
    private static int averageColor(Identifier item) {
        var texture = Identifier.fromNamespaceAndPath(item.getNamespace(), "textures/item/" + item.getPath() + ".png");
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return FALLBACK;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getPixel(x, y);
                    if ((argb >>> 24) < 128) continue;
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    n++;
                }
            }
            if (n == 0) return FALLBACK;
            // The template's mid-grey shades darken the tint; lift it so the item reads as the material.
            return (lift(r / n) << 16) | (lift(g / n) << 8) | lift(b / n);
        } catch (Exception e) {
            return FALLBACK;
        }
    }

    private static int lift(long channel) {
        return (int) Math.min(255, channel * 5 / 4 + 16);
    }
}
