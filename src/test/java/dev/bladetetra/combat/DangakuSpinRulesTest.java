package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DangakuSpinRulesTest {
    @Test void sharesFortyPercentOfOneCircleNotFourFullAttacks() {
        assertEquals(DangakuCircleSlashRules.FINISH_DAMAGE_BUDGET * .4,
                DangakuSpinRules.DAMAGE_BUDGET, 1e-7);
        assertEquals(.2992D, DangakuSpinRules.DAMAGE_BUDGET, 1e-7);
        assertTrue(DangakuSpinRules.DAMAGE_BUDGET < DangakuCircleSlashRules.FINISH_DAMAGE_BUDGET);
        for (boolean critical : new boolean[]{false, true}) {
            assertEquals(DangakuSpinRules.DAMAGE_BUDGET,
                    .325D * DangakuSpinRules.damageScale(critical) * 4 * (critical ? 1.1F : 1), 1e-9);
        }
    }
    @Test void repeatRequiresGroundedHeldAttackAndNothingElse() {
        for (int flags = 0; flags < 32; flags++) {
            boolean held = (flags & 1) != 0, ground = (flags & 2) != 0, busy = (flags & 4) != 0,
                    flying = (flags & 8) != 0, riding = (flags & 16) != 0;
            assertEquals(held && ground && !busy && !flying && !riding,
                    DangakuSpinRules.canRepeat(held, ground, busy, flying, riding));
        }
    }
    @Test void cadenceAndMovementPenaltyStayBounded() {
        assertEquals(6, DangakuSpinRules.HOLD_TICKS);
        assertEquals(12, DangakuSpinRules.CIRCLE_TICKS);
        assertEquals(.75D, (DangakuSpinRules.DAMAGE_SHARE / DangakuSpinRules.CIRCLE_TICKS) / (.8D / 18), 1e-7);
        assertEquals(.75D, 1 + DangakuSpinRules.MOVEMENT_PENALTY);
        assertEquals(9, DangakuSpinRules.RECOVERY_TICKS);
    }
}
