package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Unlit vertex colors keep the hot tip luminous regardless of native blade normals. */
final class AkatsukiBloodRenderType extends RenderType {
    static final RenderType BLOOD=create("akatsuki_blood",DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,512,false,true,CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                    .createCompositeState(false));
    private AkatsukiBloodRenderType() {
        super("akatsuki_blood",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,512,false,true,()->{},()->{});
    }
}
