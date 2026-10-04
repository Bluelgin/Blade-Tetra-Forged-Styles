package dev.bladetetra.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyFusionStoredAbilityTest {
    @Test void emptyAndInvalidMetadataAreNotAbilityIds() {
        assertNull(LegacyFusionAbilitySync.storedAbility(null));
        assertNull(LegacyFusionAbilitySync.storedAbility(""));
        assertNull(LegacyFusionAbilitySync.storedAbility("   "));
        assertNull(LegacyFusionAbilitySync.storedAbility("Invalid Ability"));
    }
    @Test void actualAbilityIdsAreRetained() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("slashblade", "judgement_cut"),
                LegacyFusionAbilitySync.storedAbility("slashblade:judgement_cut"));
    }
}
