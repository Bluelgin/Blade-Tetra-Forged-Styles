package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DivineFireRescueStateTest {
    @Test void thresholdIsRelativeAndDeadPlayersCannotBeRescued() {
        var state = new DivineFireRescueState();
        assertFalse(state.tryStart(6.01F, 20, 10));
        assertFalse(state.tryStart(0, 20, 10));
        assertFalse(state.tryStart(-1, 20, 10));
        assertFalse(state.tryStart(Float.NaN, 20, 10));
        assertFalse(state.tryStart(1, 0, 10));
        assertFalse(state.tryStart(1, Float.POSITIVE_INFINITY, 10));
        assertTrue(state.tryStart(6, 20, 10));
        assertTrue(new DivineFireRescueState().tryStart(30, 100, 10));
    }
    @Test void sixPulsesThenExpiresWithoutRefundingBudget() {
        var state = new DivineFireRescueState();
        assertFalse(state.active(100));
        assertTrue(state.tryStart(1, 20, 100));
        int pulses = 0;
        for (long now = 100; now < 240; now++) if (state.healingPulse(now)) pulses++;
        assertEquals(6, pulses);
        assertEquals(120, state.remaining(100));
        assertEquals(1, state.remaining(219));
        assertEquals(0, state.remaining(220));
        assertFalse(state.active(99));
        assertFalse(state.tryStart(1, 20, 1000));
        assertTrue(state.used());
    }
    @Test void HealingIsCappedAndParticipantsHaveSeparateBudgets() {
        assertEquals(12, DivineFireRescueState.healingAmount(20) * 6);
        assertEquals(12, DivineFireRescueState.healingAmount(200) * 6);
        assertEquals(6, DivineFireRescueState.healingAmount(10) * 6);
        var first = new DivineFireRescueState();
        var second = new DivineFireRescueState();
        assertTrue(first.tryStart(1, 20, 5));
        assertTrue(second.tryStart(1, 20, 80));
        assertTrue(second.active(130));
        assertFalse(first.active(130));
    }
    @Test void serverCannotSpawnOffensiveCompanionOrReviveAndRewardFlowIsRetained() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/MikageDivineSupportManager.java"));
        assertFalse(manager.contains("addFreshEntity"));
        assertFalse(manager.contains("target.hurt"));
        String defeat = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/DivineDomainProtectionEvents.java"));
        assertFalse(defeat.contains("support.guard"));
        String domain = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/DivineDomainManager.java"));
        assertTrue(domain.contains("DivineDomainAfterword.grant(player)"));
        assertTrue(domain.contains("tier.grantsDeadThoughtSeal()"));
        assertTrue(domain.contains("tier.waves()"));
    }
}
