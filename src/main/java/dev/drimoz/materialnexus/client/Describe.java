package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.datapack.PackContent;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import dev.drimoz.materialnexus.datapack.RecipeRewrites;
import net.minecraft.network.chat.Component;

/** Choices and pack effects in words, for the preview drawer. */
final class Describe {
    private Describe() { }

    /** One canonical choice in words. */
    static Component describe(PolicyEditor.Entry e) {
        Component material = Names.material(e.material());
        Component form = Names.form(e.form());
        if (!e.valid()) {
            return Component.translatable("screen.materialnexus.preview_invalid", material, form, e.to().toString());
        }
        Component from = e.from().map(id -> (Component) Component.literal(id.toString()))
                .orElse(Component.translatable("screen.materialnexus.none"));
        if (e.to().equals(dev.drimoz.materialnexus.datapack.CanonicalChange.RESET)) {
            return Component.translatable("screen.materialnexus.preview_reset", material, form, from);
        }
        return Component.translatable("screen.materialnexus.preview_line", material, form, from, e.to().toString());
    }

    /** One pack effect in words. */
    static Component describe(PackContent.Effect e) {
        return switch (e.kind()) {
            case PackContent.TAG_REMOVE -> Component.translatable("screen.materialnexus.effect.tag_remove", e.target().toString(), e.item().toString());
            case PackContent.ITEM_CONVERSION -> Component.translatable("screen.materialnexus.effect.item_conversion", e.item().toString(), e.target().toString());
            case RecipeRewrites.REWRITE -> Component.translatable("screen.materialnexus.effect.recipe_rewrite", e.target().toString(), e.item().toString());
            case RecipeRewrites.DISABLE -> Component.translatable("screen.materialnexus.effect.recipe_disable", e.target().toString());
            case RecipeRewrites.UNSUPPORTED -> Component.translatable("screen.materialnexus.effect.recipe_unsupported", e.target().toString(), e.item().toString());
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.PROCESS -> processLine(e);
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.DISABLE -> Component.translatable("screen.materialnexus.effect.process_disable", e.target().toString(), e.item().toString());
            case dev.drimoz.materialnexus.datapack.ProcessPlanner.UNSUPPORTED -> Component.translatable("screen.materialnexus.effect.process_unsupported", e.target().toString(), e.item().toString());
            case PackContent.RECIPE_INVALID -> Component.translatable("screen.materialnexus.effect.recipe_invalid", e.target().toString(), e.item().toString());
            case "data_edit" -> Component.translatable("screen.materialnexus.effect.data_edit", e.item().getPath(), e.target().toString());
            case "data_reset" -> Component.translatable("screen.materialnexus.effect.data_reset", e.item().getPath(), e.target().toString());
            case "data_invalid" -> Component.translatable("screen.materialnexus.effect.data_invalid", e.item().getPath(), e.target().toString());
            case PackContent.ITEM_CREATE -> Component.translatable("screen.materialnexus.effect.item_create", e.target().toString(), e.item().toString());
            case PackContent.ALMOST_UNIFIED -> Component.translatable("screen.materialnexus.effect.almost_unified",
                    Component.translatable("materialnexus.au_domain." + e.target().getPath()));
            default -> Component.translatable("screen.materialnexus.effect.conversion", e.item().toString(), e.target().toString());
        };
    }

    /** The ratio is in the generated recipe id: process/&lt;form&gt;/&lt;material&gt;/&lt;input&gt;/&lt;in&gt;/&lt;out&gt;/&lt;machine ns&gt;/&lt;machine path&gt;. */
    private static Component processLine(PackContent.Effect e) {
        String[] p = e.target().getPath().split("/", 8);
        if (p.length < 8) return Component.literal(e.target().toString());
        return Component.translatable("screen.materialnexus.effect.process_recipe", p[6] + ":" + p[7], p[4], Names.form(p[3]),
                p[5], e.item().toString(), Names.material(p[2]));
    }
}
