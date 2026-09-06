package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageEntity;
import dev.bladetetra.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class MikageRenderer extends LivingEntityRenderer<MikageEntity, PlayerModel<MikageEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BladeTetra.MOD_ID, "textures/entity/mikage.png");
    private static final ResourceLocation BLINK_TEXTURE =
            new ResourceLocation(BladeTetra.MOD_ID, "textures/entity/mikage_blink.png");

    public MikageRenderer(EntityRendererProvider.Context context) {
        super(context, new MikagePlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5F);
        // Keep SlashBlade's own weapon layer, but let Mikage's restrained custom
        // model drive the body instead of impersonating a fully animated player.
        addLayer(new LayerMainBlade<>(this));
    }

    @Override
    public void render(MikageEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        if (entity.isMoonEchoActive()) {
            pose.pushPose();
            pose.translate(0.0D, 1.1D, 0.0D);
            MikagePhantomSwordRenderer.renderSummonedSword(pose, buffers, light,
                    yaw, -18.0F, 0xFF1028, 1.08F);
            pose.popPose();
        }
        if (!entity.isMoonEchoActive() && !entity.isSwordWheelDeployed()
                && entity.getSwordWheelCount() > 0) {
            int count = entity.getSwordWheelCount();
            for (int slot = 0; slot < count; slot++) {
                double angle = (entity.tickCount + partialTick) * 0.035D
                        + slot * Math.PI * 2.0D / count;
                pose.pushPose();
                pose.translate(Math.cos(angle) * 1.35D,
                        1.15D + Math.sin(angle * 2.0D) * 0.22D,
                        Math.sin(angle) * 1.35D);
                MikagePhantomSwordRenderer.renderSummonedSword(pose, buffers, light,
                        (float) Math.toDegrees(angle), -22.0F, 0xC91938, 0.78F);
                pose.popPose();
            }
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MikageEntity entity) {
        int blink = Math.floorMod(entity.tickCount + entity.getId() * 17, 94);
        return blink < 4 ? BLINK_TEXTURE : TEXTURE;
    }

    /** Player-shaped sword forms driven by Mikage's server-synchronised combat state. */
    private static final class MikagePlayerModel extends PlayerModel<MikageEntity> {
        MikagePlayerModel(ModelPart root) {
            super(root, true);
        }

        @Override
        public void setupAnim(MikageEntity entity, float limbSwing, float limbSwingAmount,
                float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            float p = entity.getActionProgress(ageInTicks - entity.tickCount);
            switch (entity.getAction()) {
                case IAIDO_READY -> poseIaidoReady(p);
                case IAIDO_DRAW -> poseIaidoDraw(p);
                case COMBO_READY -> poseComboReady(p);
                case COMBO_SLASH -> poseComboSlash(p);
                case HEAVY_READY -> poseHeavyReady(p);
                case HEAVY_CLEAVE -> poseHeavyCleave(p);
                case CAST_READY -> poseCastReady(p);
                case CAST_SLASH -> poseCastSlash(p);
                case AERIAL_CAST -> poseAerial(p);
                case RITUAL -> poseRitual(p);
                case STAGGERED -> poseStaggered(p);
                default -> poseSwordIdle();
            }
            // PlayerModel copies these before our custom pose is applied. Copy
            // again so sleeves, jacket and trousers remain attached to the skin.
            hat.copyFrom(head);
            jacket.copyFrom(body);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
        }

        private void poseSwordIdle() {
            rightArm.xRot = -0.28F;
            rightArm.yRot = -0.12F;
        }

        private void poseIaidoReady(float p) {
            body.yRot = -0.24F;
            body.xRot = 0.12F;
            rightArm.xRot = -0.55F;
            rightArm.yRot = -1.05F;
            rightArm.zRot = 0.18F;
            leftArm.xRot = -0.42F;
            leftArm.yRot = 0.82F;
            rightLeg.xRot += 0.18F;
            leftLeg.xRot -= 0.18F;
        }

        private void poseIaidoDraw(float p) {
            float swing = Mth.sin(Math.min(1.0F, p * 1.55F) * Mth.PI);
            body.yRot = Mth.lerp(p, -0.35F, 0.62F);
            rightArm.xRot = -1.15F + swing * 0.55F;
            rightArm.yRot = Mth.lerp(p, -1.25F, 0.72F);
            rightArm.zRot = -0.48F * swing;
            leftArm.xRot = -0.55F + 0.30F * swing;
            leftArm.yRot = 0.80F - 1.15F * p;
        }

        private void poseComboReady(float p) {
            body.yRot = 0.24F;
            rightArm.xRot = -1.08F;
            rightArm.yRot = 0.62F;
            leftArm.xRot = -0.48F;
            leftArm.yRot = -0.35F;
        }

        private void poseComboSlash(float p) {
            float cycle = p * Mth.PI * 5.0F;
            body.yRot = Mth.sin(cycle) * 0.46F;
            rightArm.xRot = -1.20F + Mth.cos(cycle) * 0.50F;
            rightArm.yRot = Mth.sin(cycle) * 1.05F;
            rightArm.zRot = -0.25F + Mth.cos(cycle) * 0.32F;
            leftArm.xRot = -0.45F;
        }

        private void poseHeavyReady(float p) {
            body.xRot = -0.10F;
            rightArm.xRot = -2.55F;
            rightArm.yRot = -0.18F;
            leftArm.xRot = -2.25F;
            leftArm.yRot = 0.25F;
        }

        private void poseHeavyCleave(float p) {
            float strike = Mth.sin(Math.min(1.0F, p * 1.35F) * Mth.PI);
            body.xRot = 0.18F + strike * 0.28F;
            rightArm.xRot = Mth.lerp(p, -2.65F, 0.75F);
            rightArm.yRot = -0.20F + strike * 0.45F;
            leftArm.xRot = Mth.lerp(p, -2.25F, 0.55F);
            leftArm.yRot = 0.20F;
        }

        private void poseCastReady(float p) {
            rightArm.xRot = -1.55F;
            rightArm.yRot = -0.55F;
            leftArm.xRot = -1.10F;
            leftArm.yRot = 0.60F;
            body.yRot = -0.12F;
        }

        private void poseCastSlash(float p) {
            rightArm.xRot = Mth.lerp(p, -1.85F, -0.25F);
            rightArm.yRot = Mth.lerp(p, -0.85F, 0.92F);
            body.yRot = Mth.lerp(p, -0.30F, 0.40F);
            leftArm.xRot = -0.65F;
        }

        private void poseAerial(float p) {
            body.xRot = -0.18F;
            rightArm.xRot = -2.65F + p * 0.42F;
            rightArm.zRot = -0.42F;
            leftArm.xRot = -1.35F;
            leftArm.zRot = 0.38F;
            rightLeg.xRot = 0.48F;
            leftLeg.xRot = -0.35F;
        }

        private void poseRitual(float p) {
            rightArm.xRot = -1.48F;
            rightArm.yRot = -0.10F;
            leftArm.xRot = -1.42F;
            leftArm.yRot = 0.10F;
            head.xRot = 0.16F;
        }

        private void poseStaggered(float p) {
            body.xRot = 0.42F;
            head.xRot = 0.55F;
            rightArm.xRot = -0.25F;
            rightArm.yRot = -0.65F;
            rightArm.zRot = 0.28F;
            leftArm.xRot = -0.75F;
            leftArm.yRot = 0.42F;
            rightLeg.xRot = 0.82F;
            leftLeg.xRot = -0.18F;
        }
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.MIKAGE.get(), MikageRenderer::new);
            event.registerEntityRenderer(ModEntities.MIKAGE_PHANTOM_SWORD.get(),
                    MikagePhantomSwordRenderer::new);
            event.registerEntityRenderer(ModEntities.MIKAGE_ECHO.get(), MikageEchoRenderer::new);
        }
    }
}
