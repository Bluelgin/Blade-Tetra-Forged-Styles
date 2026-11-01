package dev.bladetetra.easteregg;

import dev.bladetetra.combat.BladeStyle;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy.*;
import static org.junit.jupiter.api.Assertions.*;

class SoulSkillStateTest {
    @Test void allSixPairsCanSelectBothMembersWithoutChangingThePrimary() {
        var values = new SoulLegacyState.Legacy[] { AKATSUKI, KYOUKA, SENBONZAKURA, RAIKIRI };
        for (int a = 0; a < values.length; a++) for (int b = a + 1; b < values.length; b++) {
            var pair = EnumSet.of(values[a], values[b]);
            assertEquals(values[a], SoulSkillState.choose(pair, 0, values[a]));
            assertEquals(values[b], SoulSkillState.choose(pair, values[b].ordinal(), values[a]));
            assertEquals(values[a], SoulSkillState.choose(pair, values[a].ordinal(), values[a]));
        }
    }
    @Test void malformedAndRemovedSelectionsFallBackToAnInscribedSoul() {
        var pair = EnumSet.of(KYOUKA, RAIKIRI);
        for (int saved : new int[] {-1, 0, 1, 3, 5, Integer.MAX_VALUE})
            assertEquals(KYOUKA, SoulSkillState.choose(pair, saved, KYOUKA));
        assertEquals(RAIKIRI, SoulSkillState.choose(EnumSet.of(RAIKIRI), KYOUKA.ordinal(), NONE));
        assertEquals(NONE, SoulSkillState.choose(EnumSet.noneOf(SoulLegacyState.Legacy.class), 4, AKATSUKI));
    }
    @Test void styleRestrictionsDoNotSilentlyGrantAnotherSoul() {
        for (var style : BladeStyle.values()) {
            assertEquals(style == BladeStyle.IAIDO, SoulSkillState.allowsStyle(KYOUKA, style));
            assertEquals(style == BladeStyle.RENGEKI, SoulSkillState.allowsStyle(SENBONZAKURA, style));
            assertTrue(SoulSkillState.allowsStyle(AKATSUKI, style));
            assertTrue(SoulSkillState.allowsStyle(RAIKIRI, style));
            assertFalse(SoulSkillState.allowsStyle(NONE, style));
        }
    }
}
