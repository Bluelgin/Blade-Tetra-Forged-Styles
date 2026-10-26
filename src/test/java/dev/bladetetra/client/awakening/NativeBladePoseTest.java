package dev.bladetetra.client.awakening;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBladePoseTest {
    @Test void nativeEndpointAndAltarAreContinuousAndSourceMatrixIsNotMutated() {
        NativeBladePose captured = new NativeBladePose();
        Matrix4f nativePose = new Matrix4f().translation(1,2,3).rotateZ(.2F).scale(.0078125F);
        Matrix4f source = new Matrix4f(nativePose);
        captured.capture(null,nativePose,null);
        for(float progress:new float[]{0,.25F,.5F,.75F,1}) {
            PoseStack target = new PoseStack();
            target.translate(5,6,7); target.scale(.0078125F,.0078125F,.0078125F);
            captured.blend(target,progress);
            assertEquals(1+4*progress,target.last().pose().m30(),.00001);
            assertEquals(2+4*progress,target.last().pose().m31(),.00001);
            assertEquals(3+4*progress,target.last().pose().m32(),.00001);
            assertTrue(Float.isFinite(target.last().normal().m00()));
        }
        assertEquals(source,nativePose);
        captured.reset();
        PoseStack untouched = new PoseStack();
        captured.blend(untouched,0);
        assertEquals(new Matrix4f(),untouched.last().pose());
    }
}
