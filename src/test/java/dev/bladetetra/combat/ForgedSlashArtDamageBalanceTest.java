package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgedSlashArtDamageBalanceTest {
    @Test
    void composedReleaseDealsLessThanTwoFullNativeSignatures() {
        float primary = ForgedSlashArtDamageBalance.phaseScale(
                ForgedSlashArtHandler.Phase.PRIMARY);
        float secondary = ForgedSlashArtDamageBalance.phaseScale(
                ForgedSlashArtHandler.Phase.SECONDARY);

        assertEquals(1.50F, primary + secondary, 0.001F);
        assertTrue(primary > secondary);
    }
}
