package dev.drimoz.materialnexus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.item.CreatedItem;
import dev.drimoz.materialnexus.registry.MnxItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created items (MNX-039) need no asset files: each one uses its form's grayscale template model, tinted with the
 * material colour (from {@code items.json}, else the average colour of the material's ingot texture).
 */
@EventBusSubscriber(modid = MaterialNexus.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreatedItemsClient {
    private static final int FALLBACK = 0xA0A0A0;
    private static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();

    private CreatedItemsClient() { }

    private static ModelResourceLocation template(String form) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "item/template_" + form));
    }

    @SubscribeEvent
    public static void registerTemplates(ModelEvent.RegisterAdditional event) {
        dev.drimoz.materialnexus.item.CreatedItems.FORMS.forEach(form -> event.register(template(form.name())));
    }

    @SubscribeEvent
    public static void useTemplates(ModelEvent.ModifyBakingResult event) {
        for (var item : MnxItems.CREATED) {
            var model = event.getModels().get(template(item.get().entry().form().name()));
            if (model != null) event.getModels().put(ModelResourceLocation.inventory(item.getId()), model);
        }
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Item event) {
        for (var item : MnxItems.CREATED) {
            event.register((stack, tint) -> 0xFF000000 | color(((CreatedItem) stack.getItem()).entry()), item.get());
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
