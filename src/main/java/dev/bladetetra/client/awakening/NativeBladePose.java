package dev.bladetetra.client.awakening;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reuses the live SlashBlade/MMD pose instead of guessing third-person hand bones. */
public final class NativeBladePose {
    private final Vector3f translation = new Vector3f();
    private final Vector3f scale = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private boolean valid;
    public void capture(Matrix4f worldFromView, Matrix4f nativeMatrix, Vec3 camera) {
        Matrix4f world = new Matrix4f(nativeMatrix);
        world.getTranslation(translation); world.getScale(scale);
        world.getUnnormalizedRotation(rotation).normalize();
        valid = Float.isFinite(translation.x) && Float.isFinite(translation.y)
                && Float.isFinite(translation.z) && scale.x>0 && scale.x<.1F;
    }
    public void reset() { valid=false; }

    /** Native and altar are both in this frame's render space; quaternion interpolation avoids shearing. */
    public void blend(PoseStack target, float flight) {
        if(!valid || flight>=1) return;
        Matrix4f altar = target.last().pose();
        Vector3f position=altar.getTranslation(new Vector3f());
        Vector3f targetScale=altar.getScale(new Vector3f());
        Quaternionf targetRotation=altar.getUnnormalizedRotation(new Quaternionf()).normalize();
        position.set(translation).lerp(altar.getTranslation(new Vector3f()),flight);
        targetScale.set(scale).lerp(altar.getScale(new Vector3f()),flight);
        targetRotation.set(rotation).slerp(altar.getUnnormalizedRotation(new Quaternionf()).normalize(),flight);
        altar.translationRotateScale(position,targetRotation,targetScale);
        target.last().normal().set(altar).invert().transpose();
    }
}
