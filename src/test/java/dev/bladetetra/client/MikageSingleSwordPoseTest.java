package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MikageSingleSwordPoseTest {
    @Test void boundaryKeepsStrikeUntilDescentAndThenReturnsToIdle() {
        assertEquals(MikageSingleSwordPose.IDLE, MikageSingleSwordPose.boundary(0));
        assertEquals(MikageSingleSwordPose.READY, MikageSingleSwordPose.boundary(194));
        assertEquals(MikageSingleSwordPose.STRIKE, MikageSingleSwordPose.boundary(206));
        assertEquals(MikageSingleSwordPose.STRIKE, MikageSingleSwordPose.boundary(215));
        assertPoseNear(MikageSingleSwordPose.IDLE, MikageSingleSwordPose.boundary(230));
    }
    @Test void transitionsHaveNoPoseJumpAtPhaseBoundaries() {
        for (float tick : new float[]{32, 194, 206, 216, 230})
            assertPoseNear(MikageSingleSwordPose.boundary(tick-.001F), MikageSingleSwordPose.boundary(tick+.001F));
        for (float p : new float[]{.45F, .65F})
            assertPoseNear(MikageSingleSwordPose.slash(p-.00001F), MikageSingleSwordPose.slash(p+.00001F));
        assertPoseNear(MikageSingleSwordPose.READY, MikageSingleSwordPose.slash(0));
        assertPoseNear(MikageSingleSwordPose.IDLE, MikageSingleSwordPose.slash(1));
    }
    @Test void layerCannotRenderASecondWaistWeaponOrNativeMmdHolder() throws Exception {
        String layer = Files.readString(Path.of("src/main/java/dev/bladetetra/client/MikageHeldBladeLayer.java"));
        assertFalse(layer.contains("LayerMainBlade"));
        assertFalse(layer.contains("\"sheath\""));
        assertFalse(layer.contains("\"LeftHand\""));
        assertTrue(layer.contains("translateToHand(HumanoidArm.RIGHT"));
        assertFalse(layer.contains("MikageTailoredModel"));
        assertTrue(layer.contains("state.isBroken() ? \"blade_damaged\" : \"blade\""));
    }
    @Test void playerSkinModelNeedsNoCustomGeometryOrResourceParser() throws Exception {
        String model = Files.readString(Path.of("src/main/java/dev/bladetetra/client/MikageSkinModel.java"));
        assertTrue(model.contains("extends PlayerModel"));
        assertFalse(model.contains("JsonParser"));
        assertFalse(model.contains("getResourceManager"));
        assertTrue(model.contains("rightSleeve.copyFrom(rightArm)"));
    }
    private static void assertPoseNear(MikageSingleSwordPose.Pose a, MikageSingleSwordPose.Pose b) {
        assertEquals(a.lean(),b.lean(),.0001); assertEquals(a.turn(),b.turn(),.0001);
        assertEquals(a.armX(),b.armX(),.0001); assertEquals(a.armY(),b.armY(),.0001);
        assertEquals(a.armZ(),b.armZ(),.0001); assertEquals(a.elbow(),b.elbow(),.0001);
        assertEquals(a.wrist(),b.wrist(),.0001); assertEquals(a.leftX(),b.leftX(),.0001);
    }
}
