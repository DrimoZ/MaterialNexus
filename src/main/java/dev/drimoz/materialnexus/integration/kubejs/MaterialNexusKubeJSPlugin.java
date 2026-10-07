package dev.drimoz.materialnexus.integration.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS plugin (MNX-067), listed in {@code kubejs.plugins.txt}: KubeJS loads it only when installed, so this is the
 * only class referencing KubeJS. Scripts get {@code MaterialNexus} ({@link MaterialNexusScripts}).
 */
public final class MaterialNexusKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MaterialNexus", MaterialNexusScripts.class);
    }
}
