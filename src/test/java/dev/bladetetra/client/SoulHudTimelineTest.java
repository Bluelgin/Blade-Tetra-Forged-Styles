package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoulHudTimelineTest {
    @Test void idleAndOrdinaryTimePassingNeverRevealASeal() {
        var hud = new SoulHudTimeline();
        for (int soul = 1; soul <= 4; soul++) assertEquals(0, hud.alpha(soul, 1000));
        hud.update(1000, 0, 0);
        assertEquals(0, hud.alpha(2, 1001));
    }
    @Test void confirmedSkillsHaveIndependentHoldAndFadeWindows() {
        var hud = new SoulHudTimeline();
        hud.update(0, 2, 0); // Kyouka.
        hud.update(20, 4, 0); // Sakura does not extend Kyouka.
        assertEquals(1, hud.alpha(2, 60));
        assertEquals(.5F, hud.alpha(2, 64));
        assertEquals(0, hud.alpha(2, 68));
        assertEquals(1, hud.alpha(3, 68));
        hud.update(70, 4, 0);
        assertEquals(1, hud.alpha(3, 129));
        assertEquals(0, hud.alpha(3, 138));
    }
    @Test void sustainedSkillsStayVisibleWithoutHeartbeatsAndFadeAfterEnding() {
        var hud = new SoulHudTimeline();
        hud.update(0, 8, 8);
        assertEquals(1, hud.alpha(4, 100000));
        hud.update(100000, 0, 0);
        assertEquals(1, hud.alpha(4, 100060));
        assertEquals(.5F, hud.alpha(4, 100064));
        assertEquals(0, hud.alpha(4, 100068));
    }
    @Test void switchPreviewDoesNotEraseExistingSkillActivityAndResetClearsEverything() {
        var hud = new SoulHudTimeline();
        hud.update(0, 2, 2);
        hud.preview(10, 3);
        assertEquals(1, hud.alpha(2, 100));
        assertEquals(.5F, hud.alpha(3, 54));
        assertEquals(0, hud.alpha(3, 58));
        assertEquals(0, hud.pulseAge(3, 10));
        hud.reset();
        for (int soul = 1; soul <= 4; soul++) assertEquals(0, hud.alpha(soul, 11));
    }
}
