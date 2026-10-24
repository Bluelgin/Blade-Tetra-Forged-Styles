package dev.bladetetra.aura;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordAuraDamageTest {
    @Test void neutralDefaultPreservesDamage() {
        assertEquals(9.5F, SwordAuraDamage.scale(9.5D, 1.0D));
    }

    @Test void multiplierCanDisableReduceOrIncreaseDamage() {
        assertEquals(0.0F, SwordAuraDamage.scale(20.0D, 0.0D));
        assertEquals(10.0F, SwordAuraDamage.scale(20.0D, 0.5D));
        assertEquals(40.0F, SwordAuraDamage.scale(20.0D, 2.0D));
    }

    @Test void invalidDamageDoesNotReachTheDamageApi() {
        assertEquals(0.0F, SwordAuraDamage.scale(Double.NaN, 1.0D));
        assertEquals(0.0F, SwordAuraDamage.scale(Double.POSITIVE_INFINITY, 1.0D));
        assertEquals(0.0F, SwordAuraDamage.scale(-1.0D, 1.0D));
        assertEquals(Float.MAX_VALUE, SwordAuraDamage.scale(Float.MAX_VALUE, 10.0D));
    }
}
