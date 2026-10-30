package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningPresentationTimelineTest {
    @Test void allFourSoulsHaveNamesAndUniqueTints() {
        assertEquals(120, AwakeningPresentationTimeline.WORLD_END);
        assertEquals(120, AwakeningPresentationTimeline.OVERLAY_END);
        assertTrue(AwakeningPresentationTimeline.NAME_START
                < AwakeningPresentationTimeline.IMPACT);
        assertTrue(AwakeningPresentationTimeline.IMPACT
                < AwakeningPresentationTimeline.FADE_START);
        java.util.Set<Integer> tones = new java.util.HashSet<>();
        for (int soul = 1; soul <= 4; soul++) {
            assertTrue(AwakeningPresentationTimeline.validSoul(soul));
            assertTrue(AwakeningPresentationTimeline.nameKey(soul).contains(".true_name."));
            tones.add(AwakeningPresentationTimeline.tint(soul));
        }
        assertEquals(4, tones.size());
        assertFalse(AwakeningPresentationTimeline.validSoul(0));
        assertFalse(AwakeningPresentationTimeline.validSoul(5));
    }

    @Test void revealEnvelopeHasSmoothFadeAndNoOvershoot() {
        assertEquals(0, AwakeningPresentationTimeline.fade(0), .00001);
        assertEquals(1, AwakeningPresentationTimeline.fade(90), .00001);
        assertEquals(0, AwakeningPresentationTimeline.fade(120), .00001);
        for (int tick = 0; tick <= 120; tick++) {
            assertTrue(AwakeningPresentationTimeline.fade(tick) >= 0);
            assertTrue(AwakeningPresentationTimeline.fade(tick) <= 1);
        }
        assertEquals(0, AwakeningPresentationTimeline.smooth(-10), .00001);
        assertEquals(1, AwakeningPresentationTimeline.smooth(10), .00001);
    }
    @Test void flightIsContinuousMonotonicAndReturnsToTheSameHand() {
        for(int soul=1;soul<=4;soul++) {
            assertEquals(0,AwakeningPresentationTimeline.flight(0,soul),1e-6);
            assertEquals(1,AwakeningPresentationTimeline.flight(20,soul),1e-6);
            assertEquals(0,AwakeningPresentationTimeline.flight(120,soul),1e-6);
            float old=0;
            for(float t=0;t<=20;t+=.05F) {
                float f=AwakeningPresentationTimeline.flight(t,soul);
                assertTrue(f>=old); old=f;
            }
            old=1;
            for(float t=100;t<=120;t+=.05F) {
                float f=AwakeningPresentationTimeline.flight(t,soul);
                assertTrue(f<=old); old=f;
            }
            for(int boundary:new int[]{20,45,85,100,113})
                assertEquals(AwakeningPresentationTimeline.flight(boundary-.001F,soul),
                        AwakeningPresentationTimeline.flight(boundary+.001F,soul),.002);
        }
        assertEquals(1,AwakeningPresentationTimeline.bladeFraction(0,3));
        assertEquals(0,AwakeningPresentationTimeline.bladeFraction(70,3));
        assertEquals(1,AwakeningPresentationTimeline.bladeFraction(100,3));
        assertEquals(AwakeningPresentationTimeline.Phase.RETURN,AwakeningPresentationTimeline.phase(100));
        assertEquals(AwakeningPresentationTimeline.Phase.COMPLETE,AwakeningPresentationTimeline.phase(120));
    }
}
