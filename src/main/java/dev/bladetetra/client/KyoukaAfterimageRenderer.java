package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.*;
import dev.bladetetra.kyouka.KyoukaAfterimageEntity;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A skin-matched, cool translucent held stance. No scene capture, shaders, or duplicate player render. */
public final class KyoukaAfterimageRenderer extends LivingEntityRenderer<KyoukaAfterimageEntity, PlayerModel<KyoukaAfterimageEntity>> {
    private final PlayerModel<KyoukaAfterimageEntity> wide, slim;
    public KyoukaAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoModel(context.bakeLayer(ModelLayers.PLAYER), false), 0);
        wide = model; slim = new EchoModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new MikageHeldBladeLayer<>(this));
    }
    @Override public void render(KyoukaAfterimageEntity entity, float yaw, float partial,
            PoseStack pose, MultiBufferSource buffers, int light) {
        var player = entity.ownerId().map(id -> Minecraft.getInstance().level.getPlayerByUUID(id))
                .filter(net.minecraft.client.player.AbstractClientPlayer.class::isInstance)
                .map(net.minecraft.client.player.AbstractClientPlayer.class::cast).orElse(null);
        model = player != null && player.getModelName().equals("slim") ? slim : wide;
        ((EchoModel) model).alpha = .42F * Math.min(1, Math.max(0, (60-entity.tickCount-partial)/12));
        super.render(entity, yaw, partial, pose, buffers, 0xF000F0);
    }
    @Override public ResourceLocation getTextureLocation(KyoukaAfterimageEntity entity) {
        var id = entity.ownerId().orElse(entity.getUUID());
        var player = Minecraft.getInstance().level.getPlayerByUUID(id);
        return player instanceof net.minecraft.client.player.AbstractClientPlayer client
                ? client.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }
    @Override protected RenderType getRenderType(KyoukaAfterimageEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }
    private static final class EchoModel extends PlayerModel<KyoukaAfterimageEntity> {
        float alpha;
        EchoModel(ModelPart root, boolean slim) { super(root, slim); }
        @Override public void setupAnim(KyoukaAfterimageEntity entity, float swing, float amount, float age, float yaw, float pitch) {
            super.setupAnim(entity, 0, 0, 0, yaw, pitch);
            rightArm.xRot = -1.05F; leftArm.xRot = -.35F;
            rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm);
        }
        @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float ignoredAlpha) {
            super.renderToBuffer(pose, buffer, light, overlay, .55F, .8F, 1, alpha);
        }
    }
    @Mod.EventBusSubscriber(modid = "blade_tetra", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KYOUKA_AFTERIMAGE.get(), KyoukaAfterimageRenderer::new);
        }
    }
}
