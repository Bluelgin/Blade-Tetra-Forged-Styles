package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouwariBalanceTest {
    @Test
    void attackUsesTheSameKindOfSoftGrowthExpectedForHighStatPacks() {
        assertEquals(24.0D, DouwariBalance.effectiveAttack(24.0D), 0.0001D);
        assertEquals(30.4D, DouwariBalance.effectiveAttack(40.0D), 0.0001D);
        assertEquals(64.0D, DouwariBalance.effectiveAttack(500.0D), 0.0001D);
    }

    @Test
    void armorAndToughnessIncreaseDamageWithoutUnboundedGrowth() {
        float unarmored = DouwariBalance.damage(20.0D, 0.0D, 0.0D);
        float armored = DouwariBalance.damage(20.0D, 20.0D, 8.0D);
        float extreme = DouwariBalance.damage(20.0D, 200.0D, 200.0D);

        assertTrue(armored > unarmored);
        assertTrue(extreme >= armored);
        assertEquals(DouwariBalance.damage(20.0D, 30.0D, 0.0D),
                extreme, 0.0001F);
    }

    @Test
    void dynamicAndAbsoluteCapsContainVeryLargeInputs() {
        assertTrue(DouwariBalance.damage(24.0D, 30.0D, 0.0D) < 40.0F);
        assertEquals(56.0F,
                DouwariBalance.damage(500.0D, 200.0D, 200.0D),
                0.0001F);
    }

    @Test
    void invalidAttackInputCannotCreateDamage() {
        assertEquals(0.0F,
                DouwariBalance.damage(-10.0D, 20.0D, 8.0D),
                0.0001F);
    }
}
