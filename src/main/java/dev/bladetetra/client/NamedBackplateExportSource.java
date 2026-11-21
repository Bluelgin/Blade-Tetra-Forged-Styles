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
        String modelOwner = source.model().getNamespace();
        String textureOwner = source.texture().getNamespace();
        // A source being installed does not grant rights to redistribute its model or image.
        if (!java.util.Set.of("blade_tetra", "slashblade").contains(modelOwner)
                || !java.util.Set.of("blade_tetra", "slashblade").contains(textureOwner)) {
            throw new IOException("Third-party named portrait needs documented embedding permission");
        }
        return NamedBackplateArtwork.bake(resources, source);
    }
    private NamedBackplateExportSource() {}
}
