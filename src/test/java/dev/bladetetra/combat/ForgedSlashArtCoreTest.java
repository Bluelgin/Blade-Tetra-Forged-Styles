package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgedSlashArtCoreTest {
    @Test
    void vanillaMaterialProgressionIsBoundedAndMonotonic() {
        assertEquals(0, ForgedSlashArtCore.bonusFromPrimary(2), 0.00001);
        assertEquals(0.075, ForgedSlashArtCore.bonusFromPrimary(5), 0.00001);
        assertEquals(0.10, ForgedSlashArtCore.bonusFromPrimary(6), 0.00001);
        assertEquals(0.125, ForgedSlashArtCore.bonusFromPrimary(7), 0.00001);
        assertEquals(0.15, ForgedSlashArtCore.bonusFromPrimary(8), 0.00001);
        assertEquals(0.15, ForgedSlashArtCore.bonusFromPrimary(10000), 0.00001);
    }

    @Test
    void InvalidOrWeakAddonStatsCannotReduceOrPoisonDamage() {
        for (double primary : new double[]{-100, 0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, ForgedSlashArtCore.bonusFromPrimary(primary));
        }
        assertEquals(0.85, ForgedSlashArtCore.phaseScale(false, Float.NaN), 0.00001);
        assertEquals(0.65, ForgedSlashArtCore.phaseScale(true, -1), 0.00001);
    }

    @Test
    void BothPhasesShareTheSameCoreBonusWithoutRestoringTwoFullSignatures() {
        assertEquals(0.935, ForgedSlashArtCore.phaseScale(false, 0.10F), 0.00001);
        assertEquals(0.715, ForgedSlashArtCore.phaseScale(true, 0.10F), 0.00001);
        assertEquals(0.9775, ForgedSlashArtCore.phaseScale(false, 100), 0.00001);
        assertEquals(0.7475, ForgedSlashArtCore.phaseScale(true, 100), 0.00001);
        assertTrue(ForgedSlashArtCore.phaseScale(false, 0.15F) < 1);
        assertEquals("93.5%", ForgedSlashArtCore.percent(0.935F));
    }

    @Test
    void MaterialIdentityRetainsAddonNamespacesAndPaths() {
        assertEquals("diamond", ForgedSlashArtCore.materialKey("sa_core/diamond"));
        assertEquals("addon:metal/steel", ForgedSlashArtCore.materialKey("sa_core/addon:metal/steel"));
        assertEquals("", ForgedSlashArtCore.materialKey(null));
        assertEquals(0, ForgedSlashArtCore.bonus(""));
    }
}
