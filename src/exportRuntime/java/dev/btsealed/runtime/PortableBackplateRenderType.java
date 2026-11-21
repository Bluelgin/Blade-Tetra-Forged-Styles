package dev.btsealed.runtime;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native durability OBJ uses triangles and translucent portrait coverage, not quads/cutout. */
final class PortableBackplateRenderType extends RenderType {
    private static final Map<ResourceLocation, RenderType> CACHE = new LinkedHashMap<>(16, .75F, true);

    private PortableBackplateRenderType() {
        super("sealed_blade_backplate", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                256, false, false, () -> {}, () -> {});
    }

    static synchronized RenderType get(ResourceLocation texture) {
        RenderType type = CACHE.get(texture);
        if (type == null) {
            type = create("sealed_blade_backplate", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(POSITION_COLOR_TEX_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .createCompositeState(false));
            CACHE.put(texture, type);
            if (CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return type;
    }

}
