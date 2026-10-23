package dev.bladetetra.challenge.mikage;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MikageAuthoredSkillsTest {
    @Test void signaturesRequireOrdinaryExchangesAndIndependentCooldowns() {
        SkillPacing pacing = new SkillPacing();
        assertTrue(pacing.signatureReady("gate", 0));
        pacing.committed("gate", 10, 400, true);
        assertFalse(pacing.signatureReady("other", 20));
        pacing.committed("combo", 20, 0, false);
        assertFalse(pacing.signatureReady("other", 30));
        pacing.committed("iaido", 30, 0, false);
        assertTrue(pacing.signatureReady("other", 31));
        assertFalse(pacing.signatureReady("gate", 409));
        assertTrue(pacing.signatureReady("gate", 410));
        assertFalse(pacing.ready("combo", 79));
        assertTrue(pacing.ready("combo", 80));
        assertTrue(new SkillPacing().signatureReady("gate", 0));
    }
    @Test void poolHasNoRetiredTechniquesAndEveryAttackCommitsAfterWarning() {
        Set<String> ids = new HashSet<>();
        for (MikageMove move : MikageMove.values()) {
            assertTrue(ids.add(move.id()));
            assertTrue(move.windup >= 10);
            assertTrue(move.recovery >= 20);
            assertEquals(0, move.releases()[0]);
            for (int i = 1; i < move.releases().length; i++) assertTrue(move.releases()[i] > move.releases()[i-1]);
        }
        assertEquals(14, ids.size());
        assertTrue(MikageMove.CLEAVE.windup > MikageMove.COMBO.windup);
        assertEquals(3, MikageMove.SUPER_CUT.phase);
        assertEquals(4, MikageMove.CHASE_RAIN.releases().length);
    }
    @Test void parriedNativeCallbackMayCloseForegroundWhileTickIsRunning() {
        SkillRunner runner = new SkillRunner(); int[] cleanup = {0}, stops = {0};
        runner.start(new SkillExecution() {
            @Override public void start(CastScope scope) { scope.own(() -> cleanup[0]++); }
            @Override public Status tick() { runner.stop(StopReason.COUNTERED); return Status.COUNTERED; }
            @Override public void stop(StopReason reason) { stops[0]++; }
        });
        runner.tick(); runner.stop(SkillExecution.StopReason.CANCELLED);
        assertFalse(runner.active()); assertEquals(1, cleanup[0]); assertEquals(1, stops[0]);
    }
}
