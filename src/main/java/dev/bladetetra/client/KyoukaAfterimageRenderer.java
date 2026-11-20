package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.client.kyouka.WaterMirrorVisuals;
import dev.bladetetra.kyouka.KyoukaAfterimageEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the targetable echo using the same water film as Water Mirror Armor. */
public final class KyoukaAfterimageRenderer extends EntityRenderer<KyoukaAfterimageEntity> {
    public KyoukaAfterimageRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KyoukaAfterimageEntity entity, float yaw, float partial,
            PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partial;
        float remaining = KyoukaAfterimageEntity.LIFETIME - age;
        if (remaining <= 0) return;
        float opacity = (.28F + .44F * Math.min(1F, remaining / 6F)) * Math.min(1F, (age + 1F) / 4F);
        pose.pushPose();
        try {
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            WaterMirrorVisuals.echo(pose, buffers, age, opacity, true);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(KyoukaAfterimageEntity entity) {
        return WaterMirrorVisuals.WATER;
    }


}
