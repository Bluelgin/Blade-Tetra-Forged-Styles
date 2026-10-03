package dev.bladetetra.compat.attachments;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.resources.ResourceLocation;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.BasicModule;
import se.mickelus.tetra.module.ModuleRegistry;
import se.mickelus.tetra.module.data.ModuleData;

/** A native sword socket mounted at the pommel, with the loaded provider variants unchanged. */
public final class NativeSocketKashira {
    public static void register() {
        ModuleRegistry.instance.registerModuleType(
                new ResourceLocation("blade_tetra", "native_sword_socket"),
                (key, placeholder) -> new BasicModule(key, source(placeholder)));
    }

    private static ModuleData source(ModuleData placeholder) {
        var nativeData = DataManager.instance.moduleData.getData(new ResourceLocation("tetra", "sword/socket"));
        if (nativeData == null) return placeholder;
        var mounted = nativeData.shallowCopy();
        mounted.slots = new String[]{ModularSlashBladeItem.KASHIRA_SLOT};
        mounted.slotSuffixes = new String[]{""};
        return mounted;
    }

    private NativeSocketKashira() {}
}
