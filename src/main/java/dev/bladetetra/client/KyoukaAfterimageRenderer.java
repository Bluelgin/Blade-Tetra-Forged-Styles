package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Mirror decoys must always have a standalone EntityRenderer, even if no player is
 * tracked or a player-model replacement mod is installed. The decoy's combat and
 * aggro state remain server authoritative; a missing skin is purely visual.
 */
public final class KyoukaAfterimageRenderer extends EntityRenderer<KyoukaAfterimageEntity> {
    private final PlayerEchoRenderer playerEcho;
    private final boolean ysmInstalled;
    private boolean playerEchoFailed;

    public KyoukaAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context);
        // YSM replaces player rendering. Never send a decoy through a player-model
        // rendering path in that environment; use our independent water silhouette.
        ysmInstalled = ModList.get().isLoaded("yes_steve_model");
        playerEcho = ysmInstalled ? null : new PlayerEchoRenderer(context);
    }

    @Override
    public void render(KyoukaAfterimageEntity entity, float yaw, float partial,
            PoseStack pose, MultiBufferSource buffers, int light) {
        var level = Minecraft.getInstance().level;
        var owner = level == null ? null : entity.ownerId()
                .map(level::getPlayerByUUID)
                .filter(net.minecraft.client.player.AbstractClientPlayer.class::isInstance)
                .map(net.minecraft.client.player.AbstractClientPlayer.class::cast)
                .orElse(null);

        if (KyoukaDecoyVisualPolicy.select(ysmInstalled, owner != null, playerEchoFailed)
                == KyoukaDecoyVisualPolicy.Mode.PLAYER_ECHO) {
            try {
                playerEcho.render(entity, yaw, partial, pose, buffers, light);
                return;
            } catch (RuntimeException e) {
                // Permanently avoid this renderer instance after a compatibility
                // failure. Rendering still has a bounded, model-free visual path.
                playerEchoFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Kyouka player-model echo unavailable; using model-free water silhouette", e);
            }
        }
        try {
            renderWaterSilhouette(entity, pose, buffers, partial);
        } catch (RuntimeException e) {
            // Last resort: the game must be allowed to render the next entity.
            // No world entities, models, or render targets are created here.
            if (entity.tickCount % 20 == 0) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Kyouka water silhouette unavailable; hiding decoy visuals", e);
            }
        }
    }

    private static void renderWaterSilhouette(KyoukaAfterimageEntity entity,
            PoseStack pose, MultiBufferSource buffers, float partial) {
        float fade = Math.min(1F, Math.max(0F,
                (60F - entity.tickCount - partial) / 12F));
        if (fade <= 0) return;
        int alpha = Math.round(105F * fade);
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            VertexConsumer out = buffers.getBuffer(RenderType.lightning());
            Matrix4f matrix = pose.last().pose();

            // A deliberately simple, double-sided aquatic figure: no PlayerModel,
            // skin lookup, YSM bone/controller, held-item layer or entity clone.
            panel(out, matrix, -.22F, .76F, .22F, 1.48F, 0F, alpha);
            panel(out, matrix, -.19F, 1.51F, .19F, 1.86F, 0F, alpha);
            panel(out, matrix, -.42F, .88F, -.25F, 1.42F, 0F, alpha / 2);
            panel(out, matrix, .25F, .88F, .42F, 1.42F, 0F, alpha / 2);
            panel(out, matrix, -.21F, .08F, -.04F, .76F, 0F, alpha / 2);
            panel(out, matrix, .04F, .08F, .21F, .76F, 0F, alpha / 2);
            panel(out, matrix, .32F, .78F, .36F, 1.68F, .04F, alpha);
            // Cross-plane silhouette remains visible from oblique camera angles.
            pose.mulPose(Axis.YP.rotationDegrees(90F));
            matrix = pose.last().pose();
            panel(out, matrix, -.22F, .76F, .22F, 1.48F, 0F, alpha / 2);
            panel(out, matrix, -.19F, 1.51F, .19F, 1.86F, 0F, alpha / 2);
        } finally {
            pose.popPose();
        }
    }

    private static void panel(VertexConsumer out, Matrix4f matrix,
            float x0, float y0, float x1, float y1, float z, int alpha) {
        out.vertex(matrix, x0, y0, z).color(74, 190, 230, alpha).endVertex();
        out.vertex(matrix, x1, y0, z).color(74, 190, 230, alpha).endVertex();
        out.vertex(matrix, x1, y1, z).color(130, 225, 248, alpha).endVertex();
        out.vertex(matrix, x0, y1, z).color(130, 225, 248, alpha).endVertex();
        // Draw both sides: one visible face is insufficient when orbiting a decoy.
        out.vertex(matrix, x0, y1, z).color(130, 225, 248, alpha).endVertex();
        out.vertex(matrix, x1, y1, z).color(130, 225, 248, alpha).endVertex();
        out.vertex(matrix, x1, y0, z).color(74, 190, 230, alpha).endVertex();
        out.vertex(matrix, x0, y0, z).color(74, 190, 230, alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(KyoukaAfterimageEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    /** Used only when the real owner is tracked and YSM is not installed. */
    private static final class PlayerEchoRenderer extends
            LivingEntityRenderer<KyoukaAfterimageEntity, PlayerModel<KyoukaAfterimageEntity>> {
        private final PlayerModel<KyoukaAfterimageEntity> wide, slim;

        PlayerEchoRenderer(EntityRendererProvider.Context context) {
            super(context, new EchoModel(context.bakeLayer(ModelLayers.PLAYER), false), 0);
            wide = model;
            slim = new EchoModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            addLayer(new MikageHeldBladeLayer<>(this));
        }

        @Override
        public void render(KyoukaAfterimageEntity entity, float yaw, float partial,
                PoseStack pose, MultiBufferSource buffers, int light) {
            var level = Minecraft.getInstance().level;
            var player = level == null ? null : entity.ownerId()
                    .map(level::getPlayerByUUID)
                    .filter(net.minecraft.client.player.AbstractClientPlayer.class::isInstance)
                    .map(net.minecraft.client.player.AbstractClientPlayer.class::cast)
                    .orElse(null);
            model = player != null && player.getModelName().equals("slim") ? slim : wide;
            ((EchoModel) model).alpha = .42F * Math.min(1, Math.max(0,
                    (60-entity.tickCount-partial)/12));
            super.render(entity, yaw, partial, pose, buffers, 0xF000F0);
        }

        @Override
        public ResourceLocation getTextureLocation(KyoukaAfterimageEntity entity) {
            var level = Minecraft.getInstance().level;
            var id = entity.ownerId().orElse(entity.getUUID());
            var player = level == null ? null : level.getPlayerByUUID(id);
            return player instanceof net.minecraft.client.player.AbstractClientPlayer client
                    ? client.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin(id);
        }

        @Override
        protected RenderType getRenderType(KyoukaAfterimageEntity entity,
                boolean visible, boolean translucent, boolean glowing) {
            return RenderType.entityTranslucent(getTextureLocation(entity));
        }
    }

    private static final class EchoModel extends PlayerModel<KyoukaAfterimageEntity> {
        float alpha;

        EchoModel(ModelPart root, boolean slim) { super(root, slim); }

        @Override
        public void setupAnim(KyoukaAfterimageEntity entity, float swing,
                float amount, float age, float yaw, float pitch) {
            super.setupAnim(entity, 0, 0, 0, yaw, pitch);
            rightArm.xRot = -1.05F;
            leftArm.xRot = -.35F;
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                int overlay, float red, float green, float blue, float ignoredAlpha) {
            super.renderToBuffer(pose, buffer, light, overlay, .55F, .8F, 1, alpha);
        }
    }

    @Mod.EventBusSubscriber(modid = "blade_tetra",
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KYOUKA_AFTERIMAGE.get(),
                    KyoukaAfterimageRenderer::new);
        }
    }
}
