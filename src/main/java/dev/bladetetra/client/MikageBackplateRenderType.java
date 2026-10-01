package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Unlit inventory layers; SlashBlade OBJ emits triangles rather than vanilla quads. */
final class MikageBackplateRenderType extends RenderType {
    private static final Map<ResourceLocation, RenderType> CACHE = new LinkedHashMap<>(16, .75F, true);

    private MikageBackplateRenderType() {
        super("mikage_backplate", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                256, false, false, () -> {}, () -> {});
    }

    static RenderType get(ResourceLocation texture) {
        RenderType result = CACHE.get(texture);
        if (result == null) {
            result = create("mikage_backplate", DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES, 256, false, false,
                    CompositeState.builder()
                            // The GUI texture shader reads position/color/UV0
                            // from NEW_ENTITY without diffuse lighting or overlays.
                            .setShaderState(POSITION_COLOR_TEX_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .createCompositeState(false));
            CACHE.put(texture, result);
            if (CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return result;
    }

    static void clear() { CACHE.clear(); }
}
