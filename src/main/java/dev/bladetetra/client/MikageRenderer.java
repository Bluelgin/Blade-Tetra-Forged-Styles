package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageEntity;
import dev.bladetetra.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
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
        // Built-in single-sword rig; old character geometry remains a recovery fallback.
        addLayer(new MikageTailoredBladeLayer<>(this));
    }

    @Override
    public void render(MikageEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        if (entity.isWithinThousandGates()) return;
        if (entity.isRidingPhantomSword()) {
            pose.pushPose(); pose.translate(0, -.16, 0);
            MikagePhantomSwordRenderer.renderRideSword(pose, buffers, light, yaw, 0xFF1838, 1.05F);
            pose.popPose();
        }
        if (entity.isMoonEchoActive()) {
            pose.pushPose();
            pose.translate(0.0D, 1.1D, 0.0D);
            MikagePhantomSwordRenderer.renderSummonedSword(pose, buffers, light,
                    yaw, -18.0F, 0xFF1028, 1.08F);
            pose.popPose();
        }
        if (!entity.isRidingPhantomSword() && !entity.isMoonEchoActive() && !entity.isSwordWheelDeployed()
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
        if (((MikageTailoredModel<?>) model).hasTailoredRig()) return MikageTailoredModel.TEXTURE;
        int blink = Math.floorMod(entity.tickCount + entity.getId() * 17, 94);
        return blink < 4 ? BLINK_TEXTURE : TEXTURE;
    }

    /** Single drawn-sword forms driven by the existing synchronised action clock. */
    private static final class MikagePlayerModel extends MikageTailoredModel<MikageEntity> {
        MikagePlayerModel(ModelPart root) { super(root); }

        @Override
        public void setupAnim(MikageEntity entity, float limbSwing, float limbSwingAmount,
                float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            float p = entity.getActionProgress(ageInTicks - entity.tickCount);
            var idle = MikageSingleSwordPose.IDLE;
            var ready = MikageSingleSwordPose.READY;
            var strike = MikageSingleSwordPose.STRIKE;
            var high = MikageSingleSwordPose.HIGH;
            var pose = switch (entity.getAction()) {
                case IAIDO_READY, COMBO_READY -> MikageSingleSwordPose.blend(idle, ready, p);
                case IAIDO_DRAW, CAST_SLASH -> MikageSingleSwordPose.slash(p);
                case COMBO_SLASH -> p < .85F ? MikageSingleSwordPose.blend(ready, strike,
                        (1 - Mth.cos(p / .85F * Mth.PI * 6)) * .5F)
                        : MikageSingleSwordPose.blend(ready, idle, (p - .85F) / .15F);
                case HEAVY_READY -> MikageSingleSwordPose.blend(idle, high, p);
                case HEAVY_CLEAVE -> p < .55F ? MikageSingleSwordPose.blend(high, strike, p / .55F)
                        : MikageSingleSwordPose.blend(strike, idle, (p - .55F) / .45F);
                case CAST_READY, AERIAL_CAST -> MikageSingleSwordPose.blend(idle, ready, p);
                case GUARD -> MikageSingleSwordPose.GUARD;
                case STAGGERED -> new MikageSingleSwordPose.Pose(.30F, 0, -.25F, -.12F, -.10F,
                        -.2F, 0, -.25F, .1F, .2F);
                default -> idle;
            };
            if (entity.isBoundaryFlashPose()) pose = MikageSingleSwordPose.boundary(p * 230);
            applySwordPose(pose);
            if (entity.isRidingPhantomSword()) applySwordRide();
            if (entity.getNativeComboStage() > 0) applyNativeCombo(MikageComboBAnimation.sample(
                    entity.getNativeComboStage(), (entity.level().getGameTime() - entity.getNativeComboStart()
                    + ageInTicks - entity.tickCount) / 20F));
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
            event.registerEntityRenderer(ModEntities.MIKAGE_GATE_CORE.get(), MikageGateCoreRenderer::new);
            event.registerEntityRenderer(ModEntities.MIKAGE_GATE_SWORD.get(),
                    context -> new mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer<>(context));
        }
    }
}
