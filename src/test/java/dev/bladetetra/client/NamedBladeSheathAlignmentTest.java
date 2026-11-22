package dev.bladetetra.client;

import jp.nyatla.nymmd.MmdMotionPlayerGL2;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static dev.bladetetra.client.NamedBladeSheathAlignment.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedBladeSheathAlignmentTest {
    @Test
    void nativeStandbyUsesACommonFrame() throws Exception {
        var motion = new MmdMotionPlayerGL2();
        try (var skeleton = getClass().getResourceAsStream("/assets/slashblade/model/bladeholder.pmd");
             var animation = getClass().getResourceAsStream("/assets/slashblade/combostate/motion.vmd")) {
            motion.setPmd(new MmdPmdModel_BasicClass(new ByteArrayInputStream(skeleton.readAllBytes()), null) {});
            motion.setVmd(new MmdVmdMotion_BasicClass(new ByteArrayInputStream(animation.readAllBytes())));
        }
        for (float time : new float[] {0, 16, 32}) {
            motion.updateMotion(time);
            assertArrayEquals(hardpoint(motion, "hardpointA"), hardpoint(motion, "hardpointB"), .00001);
        }

    }

    @Test
    void compatibleSheathKeepsTheOriginalMesh() {
        var blade = profile(tube(100, 0, 1, false), new Matrix4f(), false);
        var sheath = profile(tube(105, 0, 2, false), new Matrix4f().translate(1, 0, 0), true);
        assertTrue(fit(blade, sheath).unchanged());
    }

    @Test
    void angledEccentricShellFollowsACurvedBladeWithoutMovingIt() {
        var edge = tube(100, 0, 1, true);
        var blade = profile(edge, new Matrix4f(), false);
        var faces = tube(100, 0, 2, false);
        var offset = new Matrix4f().translate(0, 4, 1).rotateZ(.06F);
        var shell = profile(faces, offset, true);
        assertFalse(covers(blade, shell, .5F));
        var fitted = fit(blade, shell);
        assertNotNull(fitted);
        var result = profile(bake(faces, offset, fitted), new Matrix4f(), true);
        assertTrue(covers(blade, result, .5F));
        assertEquals(-100, edge.get(3).vertices[0].x, .001F);
    }

    @Test
    void alternateAuthoredAxisIsResolvedByTheMountBeforeCalibration() {
        var blade = profile(tube(100, 0, 1, false), new Matrix4f(), false);
        var nativeSpace = new Matrix4f().rotateY((float) Math.PI / 2);
        var forward = profile(tube(100, 0, 2, false), new Matrix4f(), true);
        var rotated = profile(bake(tube(100, 0, 2, false), nativeSpace, fit(blade, forward)),
                new Matrix4f().rotateY((float) -Math.PI / 2), true);
        assertTrue(covers(blade, rotated, .01F));
    }

    @Test
    void rejectsShortThinAndMissingMeshes() {
        var blade = profile(tube(100, 0, 1, false), new Matrix4f(), false);
        assertNull(fit(blade, profile(tube(50, 0, 2, false), new Matrix4f(), true)));
        assertNull(fit(blade, profile(tube(105, 8, .2F, false), new Matrix4f(), true)));
        assertNull(fit(blade, profile(List.of(), new Matrix4f(), true)));
    }

    private static double[] hardpoint(MmdMotionPlayerGL2 player, String bone) {
        var matrix = new double[16];
        player._skinning_mat[player.getBoneIndexByName(bone)].getValue(matrix);
        return matrix;
    }

    private static List<Face> tube(float length, float offset, float radius, boolean curved) {
        var faces = new ArrayList<Face>();
        Vertex[] from = ring(-length, offset + (curved ? 7 : 0), radius);
        for (int segment = 1; segment <= 10; segment++) {
            float t = 1 - segment / 10F;
            Vertex[] to = ring(-length * t, offset + (curved ? 7 * t * t : 0), radius);
            for (int side = 0; side < 4; side++) {
                int next = (side + 1) % 4;
                var face = new Face();
                face.vertices = new Vertex[] {from[side], from[next], to[next], to[side]};
                faces.add(face);
            }
            from = to;
        }
        return faces;
    }

    private static Vertex[] ring(float x, float y, float r) {
        return new Vertex[] {new Vertex(x, y - r, -r), new Vertex(x, y + r, -r),
                new Vertex(x, y + r, r), new Vertex(x, y - r, r)};
    }
}
