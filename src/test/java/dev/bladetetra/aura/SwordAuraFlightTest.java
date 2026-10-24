package dev.bladetetra.aura;

import dev.bladetetra.combat.BladeStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordAuraFlightTest {
    @Test void everyStyleHasEnoughLifetimeForTheExtendedRange() {
        assertEquals(16, SwordAuraFlight.RANGE_BLOCKS);
        assertEquals(256, SwordAuraFlight.RANGE_SQUARED);
        for (BladeStyle style : BladeStyle.values()) {
            var profile = SwordAuraStyleProfile.forStyle(style);
            assertTrue(profile.speed() * (profile.lifetimeTicks() - 4) >= SwordAuraFlight.RANGE_BLOCKS);
        }
    }
    @Test void heavyAndFastStylesKeepTheirOwnFlightTiming() {
        assertEquals(12, SwordAuraFlight.lifetime(2));
        assertEquals(10, SwordAuraFlight.lifetime(2.7F));
        assertEquals(14, SwordAuraFlight.lifetime(1.7F));
        assertThrows(IllegalArgumentException.class, () -> SwordAuraFlight.lifetime(0));
        assertThrows(IllegalArgumentException.class, () -> SwordAuraFlight.lifetime(Float.NaN));
    }
}
