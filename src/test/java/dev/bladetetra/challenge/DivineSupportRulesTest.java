package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DivineSupportRulesTest {
    @Test void triangleMatchesGroundArrayRatherThanAnInvisibleCircle() {
        assertTrue(DivineSupportRules.insideArray(0,0));
        assertTrue(DivineSupportRules.insideArray(0,-5));
        assertTrue(DivineSupportRules.insideArray(4.33,2.5));
        assertTrue(DivineSupportRules.insideArray(-4.33,2.5));
        assertFalse(DivineSupportRules.insideArray(0,2.51));
        assertFalse(DivineSupportRules.insideArray(.1,-5));
        assertFalse(DivineSupportRules.insideArray(4,0));
        for(double z=-5;z<=2.5;z+=.25) {
            double edge=(z+5)/Math.sqrt(3);
            assertTrue(DivineSupportRules.insideArray(edge,z));
            assertFalse(DivineSupportRules.insideArray(edge+.001,z));
        }
    }
    @Test void phasesAreRelativeAndIncludeTheThresholds() {
        assertEquals(0,DivineSupportRules.phase(281,400));
        assertEquals(1,DivineSupportRules.phase(280,400));
        assertEquals(2,DivineSupportRules.phase(160,400));
        assertEquals(3,DivineSupportRules.phase(80,400));
        assertEquals(3,DivineSupportRules.phase(48,240));
        assertEquals(0,DivineSupportRules.phase(0,0));
    }
    @Test void companionNeverSuppliesTheFinalPointOfHealth() {
        for(float health=.125F;health<=500;health+=.125F) {
            float damage=DivineSupportRules.companionDamage(health);
            assertTrue(damage>=0 && damage<=3);
            assertTrue(damage<health);
        }
        assertEquals(0,DivineSupportRules.companionDamage(1));
        assertEquals(.5F,DivineSupportRules.companionDamage(1.5F));
    }
    @Test void windowsReplaceNotMultiplyEachOther() {
        assertEquals(1,DivineSupportRules.playerWindowMultiplier(false,false));
        assertEquals(1.1F,DivineSupportRules.playerWindowMultiplier(false,true));
        assertEquals(1.2F,DivineSupportRules.playerWindowMultiplier(true,false));
        assertEquals(1.2F,DivineSupportRules.playerWindowMultiplier(true,true));
    }
    @Test void onlyHighTiersSpawnACompanionAndAllWavesRespectHardCaps() {
        assertFalse(DivineDomainTier.GRUDGE.hasSupport());
        assertTrue(DivineDomainTier.HUNDRED_GHOSTS.hasSupport());
        assertFalse(DivineDomainTier.HUNDRED_GHOSTS.hasCompanion());
        for(var tier:DivineDomainTier.values()) for(int wave=1;wave<=tier.waves();wave++) {
            assertTrue(tier.concurrentForWave(wave)<=tier.hostileCap());
            assertTrue(tier.hostileCap()<=20);
            assertTrue(tier.reinforcementBudget()>=0 && tier.reinforcementBudget()<=12);
        }
        assertEquals(8,DivineDomainTier.ASURA.concurrentForWave(1));
        assertEquals(10,DivineDomainTier.ASURA.concurrentForWave(5));
        assertEquals(12,DivineDomainTier.AVICI.concurrentForWave(6));
    }
}
