package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.challenge.MikagePhantomSwordEntity;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Uses SlashBlade's own summoned-sword model, shared with sword-rain techniques. */
public final class MikagePhantomSwordRenderer extends EntityRenderer<MikagePhantomSwordEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation("slashblade", "model/util/ss.obj");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("slashblade", "model/util/ss.png");

    public MikagePhantomSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(MikagePhantomSwordEntity entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.isBoundarySeal()) {
            float spin = (entity.tickCount + partialTick) * 3.2F;
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                double angle = Math.toRadians(spin + i * 90.0F);
                pose.translate(Math.cos(angle) * 0.55D, 0.72D,
                        Math.sin(angle) * 0.55D);
                renderSummonedSword(pose, buffers, light,
                        spin + i * 90.0F, 90.0F, 0xFF2648, 0.82F);
                pose.popPose();
            }
            super.render(entity, yaw, partialTick, pose, buffers, light);
            return;
        }
        int revealTick = entity.getSlot() * 3;
        if (entity.tickCount + partialTick < revealTick) {
            return;
        }
        int color = entity.isSolidSword() ? 0xFF1838 : 0x9B1730;
        float reveal = Math.min(1.0F,
                (entity.tickCount + partialTick - revealTick + 1.0F) / 4.0F);
        float scale = (entity.isSolidSword()
                ? 1.16F + 0.06F * (float) Math.sin((entity.tickCount + partialTick) * 0.3F)
                : 0.93F) * (0.35F + reveal * 0.65F);
        renderSummonedSword(pose, buffers, light, yaw, -12.0F, color, scale);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    static void renderSummonedSword(PoseStack pose, MultiBufferSource buffers, int light,
            float yaw, float roll, int color, float size) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(-8.0F));
        pose.mulPose(Axis.XP.rotationDegrees(roll));
        float scale = 0.0075F * size;
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        WavefrontObject model = BladeModelManager.getInstance().getModel(MODEL);
        BladeRenderState.setCol(color, false);
        BladeRenderState.renderOverridedLuminous(ItemStack.EMPTY, model, "ss", TEXTURE,
                pose, buffers, light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MikagePhantomSwordEntity entity) {
        return TEXTURE;
    }
}
