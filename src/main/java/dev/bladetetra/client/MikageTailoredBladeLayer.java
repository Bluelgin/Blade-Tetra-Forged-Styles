package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/** One permanently drawn sword; no waist blade, sheath or player/MMD weapon pass. */
final class MikageTailoredBladeLayer<T extends LivingEntity> extends RenderLayer<T, PlayerModel<T>> {
    MikageTailoredBladeLayer(RenderLayerParent<T, PlayerModel<T>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        var stack = entity.getMainHandItem();
        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            var model = BladeModelManager.getInstance().getModel(state.getModel()
                    .orElse(new ResourceLocation("slashblade", "model/blade.obj")));
            var texture = state.getTexture().orElse(new ResourceLocation("slashblade", "model/blade.png"));
            String group = state.isBroken() ? "blade_damaged" : "blade";
            pose.pushPose();
            try {
                if (getParentModel() instanceof MikageTailoredModel<?> rig && rig.hasTailoredRig()) {
                    rig.translateToSocket("RightHand", pose);
                } else {
                    getParentModel().translateToHand(HumanoidArm.RIGHT, pose);
                    pose.translate(0, .625, 0);
                }
                // OBJ blades extend along -X. Align the grip, not the origin, with the palm.
                pose.mulPose(Axis.ZP.rotationDegrees(-35));
                float scale = MikageBladeVisuals.MODEL_SCALE;
                pose.scale(scale, scale, scale);
                pose.translate(-20, 0, 0);
                BladeRenderState.resetCol();
                MikageBladeVisuals.render(() -> {
                    BladeRenderState.renderOverrided(stack, model, group, texture, pose, buffers, light);
                    BladeRenderState.renderOverridedLuminous(stack, model, group + "_luminous", texture, pose, buffers, light);
                });
            } finally { BladeRenderState.resetCol(); pose.popPose(); }
        });
    }
}
