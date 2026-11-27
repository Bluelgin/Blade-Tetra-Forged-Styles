package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.forging.NamedBackplate;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;

/** Explicit client bridge; export never depends on the current dynamic texture cache. */
public final class NamedBackplateExportSource {
    public static NativeImage bake(ItemStack blade, ResourceManager resources) throws IOException {
        var source = NamedBackplate.source(blade);
        if (source == null) throw new IOException("Named backplate source is missing");
        return NamedBackplateArtwork.bake(resources, source);
    }
    private NamedBackplateExportSource() {}
}
