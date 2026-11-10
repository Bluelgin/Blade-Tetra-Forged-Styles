package dev.bladetetra.loot;

import dev.bladetetra.forging.LegacyImprintKind;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaltyFishLootModifierTest {
    @Test
    void skipsUnusableSourcesAndFallsBackWhenThePoolIsEmpty() {
        var sources = List.of(LegacyImprintKind.WOODEN_PUPPET,
                LegacyImprintKind.BLACK_FOX, LegacyImprintKind.WHITE_FOX);
        var random = RandomSource.create(151L);

        assertEquals(LegacyImprintKind.WHITE_FOX, SaltyFishLootModifier.chooseGuard(
                random, sources, LegacyImprintKind.WHITE_FOX::equals));
        assertNull(SaltyFishLootModifier.chooseGuard(random, sources, ignored -> false));
        assertNull(SaltyFishLootModifier.chooseGuard(random,
                List.of(LegacyImprintKind.WOODEN_PUPPET), ignored -> true));
    }
}
