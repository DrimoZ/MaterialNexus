package dev.drimoz.materialnexus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.item.CreatedItem;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created items (MNX-039) are drawn with their form's grayscale template (models written in the created items pack),
 * tinted with the material colour (from {@code items.json}, else the average colour of the material's ingot texture).
 * Only layer 1, where the templates put their texture, is tinted (MNX-073): an item with its own texture, or whose
 * model a resource pack replaced, keeps its colours.
 */
@Mod.EventBusSubscriber(modid = MaterialNexus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreatedItemsClient {
    private static final int FALLBACK = 0xA0A0A0;
    private static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();

    private CreatedItemsClient() { }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Item event) {
        for (var item : MnxItems.CREATED) {
            event.register((stack, tint) -> tint == 1 ? 0xFF000000 | color(((CreatedItem) stack.getItem()).entry()) : -1, item.get());
        }
    }

    private static int color(dev.drimoz.materialnexus.item.CreatedItems.Entry entry) {
        if (entry.color().isPresent()) return entry.color().get();
        return entry.colorFrom().map(id -> COLORS.computeIfAbsent(id, CreatedItemsClient::averageColor)).orElse(FALLBACK);
    }

    /** Average of the opaque pixels of {@code <ns>:textures/item/<path>.png}, the usual place of an item texture. */
    private static int averageColor(ResourceLocation item) {
        var texture = ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "textures/item/" + item.getPath() + ".png");
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return FALLBACK;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    if ((abgr >>> 24) < 128) continue;
                    r += abgr & 0xFF;
                    g += (abgr >> 8) & 0xFF;
                    b += (abgr >> 16) & 0xFF;
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
