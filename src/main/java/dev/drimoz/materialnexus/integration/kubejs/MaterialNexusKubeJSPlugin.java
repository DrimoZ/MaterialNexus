package dev.drimoz.materialnexus.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/**
 * KubeJS plugin (MNX-067), listed in {@code kubejs.plugins.txt}: KubeJS loads it only when installed, so this is the
 * only class referencing KubeJS. Scripts get {@code MaterialNexus} ({@link MaterialNexusScripts}).
 */
public final class MaterialNexusKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("MaterialNexus", MaterialNexusScripts.class);
    }
}
