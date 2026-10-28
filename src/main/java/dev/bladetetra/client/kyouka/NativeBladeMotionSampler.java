package dev.bladetetra.client.kyouka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.nyatla.nymmd.*;
import mods.flammpfeil.slashblade.client.renderer.model.BladeMotionManager;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.util.VectorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import dev.bladetetra.kyouka.KyoukaRules;

/** One independent MMD evaluator, reused sequentially; no player, model-bone capture or combo execution. */
public final class NativeBladeMotionSampler {
    private MmdMotionPlayerGL2 player;
    private final float[] matrix = new float[16];
    public void reset() { player = null; }
    /** Null means unsupported/expired: render the tip-down idle blade, never invent a trajectory. */
    public Matrix4f sample(ResourceLocation comboId, double elapsedTicks) {
        var combo = ComboStateRegistry.REGISTRY.get().getValue(comboId);
        if (combo == null || elapsedTicks < 0 || comboId.equals(ComboStateRegistry.NONE.getId())
                || elapsedTicks * 50 > combo.getTimeoutMS()) return null;
        var location = combo.getMotionLoc();
        if (location == null || Minecraft.getInstance().getResourceManager().getResource(location).isEmpty()) return null;
        try {
            if (player == null) {
                player = new MmdMotionPlayerGL2();
                player.setPmd(new MmdPmdModelMc(new ResourceLocation("slashblade", "model/bladeholder.pmd")));
            }
            var manager = BladeMotionManager.getInstance();
            var motion = manager.getMotion(location);
            if (motion == null || motion == manager.getMotion(null) && !location.equals(DefaultResources.ExMotionLocation)) return null;
            player.setVmd(motion);
            double start = combo.getStartFrame() * (1000.0 / 30);
            double end = combo.getEndFrame() * (1000.0 / 30);
            // 1.9.63 LayerMainBlade uses wall-clock time (not ComboState.speed) for VMD sampling.
            // Match that renderer exactly; speed still participates in getTimeoutMS above.
            double time = KyoukaRules.motionTime(elapsedTicks * 50, start, end,
                    motion.getMaxFrame() * (1000.0 / 30), 1, combo.getLoop());
            player.updateMotion((float) time);
            int bone = player.getBoneIndexByName("hardpointA");
            if (bone < 0) return null;
            player._skinning_mat[bone].getValue(matrix);
            PoseStack pose = new PoseStack();
            pose.translate(0, 1.5, 0); pose.scale(.125F, .125F, .125F);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(-1, 1, 1); pose.mulPoseMatrix(VectorHelper.matrix4fFromArray(matrix)); pose.scale(-1, 1, 1);
            pose.scale(.0625F, .0625F, .0625F);
            return new Matrix4f(pose.last().pose());
        } catch (Exception unsupported) { return null; }
    }
}
