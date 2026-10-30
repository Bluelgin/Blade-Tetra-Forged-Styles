package dev.bladetetra.client.awakening;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.bladetetra.client.awakening.MirrorRitualController.*;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;

class MirrorRitualControllerTest {
    @Test void shardsBeginAtTheExactClosedMirrorAndAllReachTheBlade() {
        assertEquals(36,FACETS.size());
        Vec3 blade=new Vec3(.12,-.2,-.7);
        for(var f:FACETS) for(var p:new Vec3[]{f.a(),f.b(),f.c()}) {
            assertTrue(vertex(f,p,85,blade).distanceTo(p)<1e-6);
            assertTrue(vertex(f,p,112,blade).distanceTo(blade)<1e-6);
        }
    }
    @Test void movementIsContinuousAndBoundedThroughBurstPauseAndAbsorption() {
        Vec3 blade=new Vec3(0,-.2,-.8);
        for(var f:FACETS) for(var p:new Vec3[]{f.a(),f.b(),f.c()}) {
            for(float t=24;t<=120;t+=.125F) {
                Vec3 a=vertex(f,p,t,blade),b=vertex(f,p,t+.001F,blade);
                assertTrue(Double.isFinite(a.length()));
                assertTrue(a.length()<4);
                assertTrue(a.distanceTo(b)<.002);
            }
            assertTrue(vertex(f,f.a(),94,blade).distanceTo(vertex(f,f.a(),97,blade))<1e-6);
        }
    }
    @Test void concealBurstAndReturnShareTheNativeBladeClock() {
        assertTrue(bladeVisible(20));assertFalse(bladeVisible(56));
        assertFalse(bladeVisible(84.99F));assertTrue(bladeVisible(85));
        assertEquals(Phase.CRACK,phase(80));assertEquals(Phase.ABSORB,phase(105));
        assertEquals(Phase.END,phase(112));
        assertEquals(1,AwakeningPresentationTimeline.flight(112,2));
        assertEquals(0,AwakeningPresentationTimeline.flight(120,2));
        for(float t=0;t<=120;t+=.125F) {
            assertTrue(bladeOffset(t,new Vec3(0,0,1)).distanceTo(bladeOffset(t+.001F,new Vec3(0,0,1)))<.001);
        }
    }
}
