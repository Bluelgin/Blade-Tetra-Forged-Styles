package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningPresentationTimelineTest {
    @Test void allFourSoulsHaveNamesAndUniqueTints() {
        assertEquals(44, AwakeningPresentationTimeline.WORLD_END);
        assertEquals(60, AwakeningPresentationTimeline.OVERLAY_END);
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
        assertEquals(1, AwakeningPresentationTimeline.fade(28), .00001);
        assertEquals(0, AwakeningPresentationTimeline.fade(60), .00001);
        for (int tick = 0; tick <= 60; tick++) {
            assertTrue(AwakeningPresentationTimeline.fade(tick) >= 0);
            assertTrue(AwakeningPresentationTimeline.fade(tick) <= 1);
        }
        assertEquals(0, AwakeningPresentationTimeline.smooth(-10), .00001);
        assertEquals(1, AwakeningPresentationTimeline.smooth(10), .00001);
    }
}
