package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwinPhaseDamageTest {
    @Test
    void focusedCutsScaleAndCapIndividually() {
        assertEquals(2.2F,
                LegacyFusionHandler.twinPhaseSlashDamage(10.0D, false, false),
                0.0001F);
        assertEquals(5.0F,
                LegacyFusionHandler.twinPhaseSlashDamage(100.0D, false, false),
                0.0001F);
    }

    @Test
    void crowdCutsTradePerTargetDamageForCoverage() {
        assertEquals(1.3F,
                LegacyFusionHandler.twinPhaseSlashDamage(10.0D, true, false),
                0.0001F);
        assertEquals(1.6F,
                LegacyFusionHandler.twinPhaseSlashDamage(10.0D, true, true),
                0.0001F);
        assertEquals(3.5F,
                LegacyFusionHandler.twinPhaseSlashDamage(100.0D, true, false),
                0.0001F);
        assertEquals(4.0F,
                LegacyFusionHandler.twinPhaseSlashDamage(100.0D, true, true),
                0.0001F);
    }
}
