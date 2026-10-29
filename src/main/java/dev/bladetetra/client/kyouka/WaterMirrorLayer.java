package dev.bladetetra.client.kyouka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bladetetra.kyouka.WaterMirrorEffect;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuses the already posed player model; no duplicate model, entity, bones or world render. */
@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WaterMirrorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation WATER = new ResourceLocation("blade_tetra", "textures/effect/kyouka/water_film.png");
    private WaterMirrorLayer(PlayerRenderer renderer) { super(renderer); }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            PlayerRenderer renderer = e.getSkin(skin);
            if (renderer != null) renderer.addLayer(new WaterMirrorLayer(renderer));
        }
    }
    public static void surface(PoseStack pose, MultiBufferSource buffers, float fade) {
        var v = buffers.getBuffer(RenderType.entityTranslucent(WATER));
        var matrix = pose.last().pose(); var normal = pose.last().normal();
        v.vertex(matrix,-.7F,.1F,0).color(.65F,.86F,1F,.35F*fade).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
        v.vertex(matrix,.7F,.1F,0).color(.65F,.86F,1F,.35F*fade).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
        v.vertex(matrix,.7F,2.5F,0).color(.65F,.86F,1F,.35F*fade).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
        v.vertex(matrix,-.7F,2.5F,0).color(.65F,.86F,1F,.35F*fade).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
    }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
            float limb, float amount, float partial, float age, float yaw, float pitch) {
        if (!p.hasEffect(WaterMirrorEffect.ARMOR.get()) || p.isInvisible()
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        float air = Math.max(0, Math.min(1, p.getAirSupply() / (float)Math.max(1, p.getMaxAirSupply())));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(WATER));
        pose.pushPose();
        try {
            pose.scale(1.012F, 1.012F, 1.012F);
            // Parent pose includes the native arm/attack motion, and leaves armor/skin intact.
            getParentModel().renderToBuffer(pose, vertices, light, OverlayTexture.NO_OVERLAY,
                    .65F, .88F, 1, (.06F + .18F * air) * (.9F + .1F * (float)Math.sin(age*.22F)));
        } finally { pose.popPose(); }
    }
}
