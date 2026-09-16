package dev.bladetetra.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bladetetra.config.ClientVisualConfig;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Ordinary textured, depth-tested OBJ pass. No entities, additive bloom or shared shader replacement. */
final class DeadThoughtModel {
    private static final ResourceLocation TEXTURE = new ResourceLocation("blade_tetra",
            "textures/effect/dead_thought/dead_thought_palette.png");
    private static final Map<String, ResourceLocation> MODELS = Stream.of("domain", "final_wheel", "rifts",
            "branch_cage", "life_remnant", "scars", "execution_line").collect(Collectors.toUnmodifiableMap(
                    s -> s, s -> new ResourceLocation("blade_tetra", "models/effect/dead_thought/" + s + ".obj")));

    static void draw(PoseStack poses, MultiBufferSource buffers, String model, Vec3 at,
            float yaw, float pitch, float roll, float sx, float sy, float sz, float alpha, String... groups) {
        float opacity = alpha * ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        if (opacity <= .005F || sx <= .001F || sy <= .001F || sz <= .001F) return;
        poses.pushPose();
        try {
            poses.translate(at.x, at.y, at.z);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.mulPose(Axis.XP.rotationDegrees(pitch));
            poses.mulPose(Axis.ZP.rotationDegrees(roll));
            poses.scale(sx, sy, sz);
            BladeRenderState.setCol((Mth.clamp((int) (opacity * 255), 0, 255) << 24) | 0xFFFFFF, false);
            var mesh = BladeModelManager.getInstance().getModel(MODELS.get(model));
            for (String group : groups) BladeRenderState.renderOverrided(ItemStack.EMPTY, mesh,
                    group, TEXTURE, poses, buffers, LightTexture.FULL_BRIGHT);
        } finally {
            BladeRenderState.resetCol(); poses.popPose();
        }
    }
    private DeadThoughtModel() {}
}
