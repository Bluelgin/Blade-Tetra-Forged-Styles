package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SignatureFusionCatalogTest {
    @Test
    void waveTwoFusionsResolveToExpectedStructuralAbilities() {
        LegacyFusion tsukumo = LegacyFusion.byId("agito_saya_yuzukitukumo_hilt");
        LegacyFusion withered = LegacyFusion.byId("tagayasan_saya_koseki_hilt");
        LegacyFusion piercing = LegacyFusion.byId("black_saya_sange_hilt");

        assertNotNull(tsukumo);
        assertNotNull(withered);
        assertNotNull(piercing);
        assertEquals(LegacyFusion.Ability.SLASH_ART, tsukumo.ability());
        assertEquals(LegacyFusion.Ability.SLASH_ART, withered.ability());
        assertEquals(LegacyFusion.Ability.SLASH_ART, piercing.ability());
        assertEquals("blade_tetra:tsukumo_cross", tsukumo.abilityId().toString());
        assertEquals("blade_tetra:withered_drive", withered.abilityId().toString());
        assertEquals("blade_tetra:piercing_void_moon", piercing.abilityId().toString());
    }
}
