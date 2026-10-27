package dev.bladetetra.client.awakening;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MirrorRitualControllerTest {
    private MirrorRitualController.Frame at(float t) {return MirrorRitualController.frame(t,Vec3.ZERO,new Vec3(0,0,1),1.62);}
    @Test void phasesUseExisting120TickLifetimeAndBreakBeat85() {
        assertEquals(MirrorRitualController.Phase.BIRTH,at(20).phase());
        assertEquals(MirrorRitualController.Phase.REFLECTIONS,at(50).phase());
        assertEquals(MirrorRitualController.Phase.CRACK,at(80).phase());
        assertFalse(at(84.99f).broken());assertTrue(at(85).broken());
        assertEquals(MirrorRitualController.Phase.RELEASE,at(105).phase());assertFalse(at(112).live());
    }
    @Test void planeAndBladeMotionAreContinuousAtPhaseBoundaries() {
        for(float t:new float[]{12,32,66,70,72,77,85,88,98,100,103,110,112}) {
            assertTrue(at(t-.001f).plane().center().distanceTo(at(t+.001f).plane().center())<.001);
            assertEquals(at(t-.001f).plane().halfWidth(),at(t+.001f).plane().halfWidth(),.001);
            assertTrue(MirrorRitualController.bladeOffset(t-.001f,new Vec3(0,0,1))
                    .distanceTo(MirrorRitualController.bladeOffset(t+.001f,new Vec3(0,0,1)))<.001);
        }
    }
    @Test void shatterStartsAsTheExactMirrorAndThenGathers() {
        assertEquals(0,at(85).scatter());assertEquals(0,at(85).gather());
        assertEquals(1,at(100).scatter());assertEquals(1,at(110).gather());
        assertEquals(1,at(85).crack());assertEquals(0,at(72).crack());
    }
}
