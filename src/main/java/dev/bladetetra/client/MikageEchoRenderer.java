package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.challenge.MikageEchoEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/** Mirror double. Broken purple sword-light distinguishes illusion from the red core. */
public final class MikageEchoRenderer extends LivingEntityRenderer<MikageEchoEntity,
        PlayerModel<MikageEchoEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            BladeTetra.MOD_ID, "textures/entity/mikage.png");

    public MikageEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.25F);
    }

    @Override
    public void render(MikageEchoEntity entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float flicker = 0.91F + 0.06F
                * (float) Math.sin((entity.tickCount + partialTick) * 0.58F);
        pose.translate(0.0D, 1.05D + (1.0F - flicker) * 0.18D, 0.0D);
        MikagePhantomSwordRenderer.renderSummonedSword(pose, buffers, light,
                yaw, -18.0F, 0x7D2A9D, 0.82F * flicker);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MikageEchoEntity entity) {
        if (((MikageTailoredModel<?>) model).hasTailoredRig()) return MikageTailoredModel.TEXTURE;
        return TEXTURE;
    }

    private static final class EchoModel extends MikageTailoredModel<MikageEchoEntity> {
        EchoModel(ModelPart root) { super(root); }
        @Override
        public void setupAnim(MikageEchoEntity entity, float swing, float amount, float age,
                float yaw, float pitch) {
            super.setupAnim(entity, swing, amount, age, yaw, pitch);
            applySwordPose(MikageSingleSwordPose.READY);
        }
    }
}
