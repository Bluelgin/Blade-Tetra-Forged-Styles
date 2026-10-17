package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.aura.SwordAuraDriveEntity;
import mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/** Native Drive geometry, sized by style rather than a separate texture pipeline. */
public final class SwordAuraRenderer extends DriveRenderer<SwordAuraDriveEntity> {
    public SwordAuraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SwordAuraDriveEntity entity, float yaw, float partialTick,
            PoseStack pose, @NotNull MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        try {
            float width = entity.getBaseSize();
            // Preserve longitudinal blade travel; change silhouette width only.
            pose.scale(width, 1.0F, 1.0F);
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
        } finally {
            pose.popPose();
        }
    }
}
