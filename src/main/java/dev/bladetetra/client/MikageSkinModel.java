package dev.bladetetra.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import java.util.Map;

/** Original slim player skin with lightweight action poses; no external cube rig. */
public class MikageSkinModel<T extends LivingEntity> extends PlayerModel<T> {
    public MikageSkinModel(ModelPart root) { super(root, true); }

    public void applySwordPose(MikageSingleSwordPose.Pose pose) {
        body.xRot = pose.lean(); body.yRot = pose.turn(); body.zRot = 0;
        rightArm.xRot = pose.armX(); rightArm.yRot = pose.armY(); rightArm.zRot = pose.armZ();
        leftArm.xRot = pose.leftX(); leftArm.yRot = pose.leftY(); leftArm.zRot = pose.leftZ();
        copyClothing();
    }

    public void applySwordRide() {
        leftLeg.xRot = -.28F; leftLeg.zRot = -.12F;
        rightLeg.xRot = .22F; rightLeg.zRot = .14F;
        copyClothing();
    }

    public void applyNativeCombo(Map<String, Vector3f> pose) {
        rotation(body, pose.get("UpperBody"));
        rotation(head, pose.get("AllHead"));
        rotation(rightArm, pose.get("RightArm"));
        rotation(leftArm, pose.get("LeftArm"));
        copyClothing();
    }

    private static void rotation(ModelPart part, Vector3f value) {
        if (value == null) return;
        part.xRot = -value.x * Mth.DEG_TO_RAD;
        part.yRot = -value.y * Mth.DEG_TO_RAD;
        part.zRot = value.z * Mth.DEG_TO_RAD;
    }

    private void copyClothing() {
        hat.copyFrom(head); jacket.copyFrom(body);
        rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg); leftPants.copyFrom(leftLeg);
    }
}
