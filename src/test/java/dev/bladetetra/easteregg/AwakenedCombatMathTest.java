package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakenedCombatMathTest {
    @Test void akatsukiStillUsesLegacyCombatWhileOtherSoulsUseV2() {
        assertFalse(AwakenedBladeCombat.isV2CombatSoul(SoulLegacyState.Legacy.AKATSUKI));
        assertFalse(AwakenedBladeCombat.isV2CombatSoul(SoulLegacyState.Legacy.NONE));
        assertTrue(AwakenedBladeCombat.isV2CombatSoul(SoulLegacyState.Legacy.KYOUKA));
        assertTrue(AwakenedBladeCombat.isV2CombatSoul(SoulLegacyState.Legacy.SENBONZAKURA));
        assertTrue(AwakenedBladeCombat.isV2CombatSoul(SoulLegacyState.Legacy.RAIKIRI));
    }

}
