package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraStanceRulesTest {
    private static ResourceLocation id(String path) {return new ResourceLocation("slashblade",path);}
    @Test void onlyActualCleaveEntryAndUpperEntryHaveStanceMeaning() {
        assertEquals(7,SakuraStanceRules.action(id("aerial_cleave")));
        assertEquals(8,SakuraStanceRules.action(id("upperslash")));
        for(String path:new String[]{"aerial_cleave_loop","aerial_cleave_landing","aerial_cleave_end",
                "upperslash_jump","upperslash_end","combo_b1","combo_b6","combo_b7","rapid_slash","sakura_end_left"})
            assertEquals(0,SakuraStanceRules.action(id(path)));
        assertEquals(0,SakuraStanceRules.action(new ResourceLocation("othermod","upperslash")));
        assertEquals(0,SakuraStanceRules.action(null));
    }
    @Test void targetStatesAreIdempotentRatherThanToggles() {
        assertTrue(SakuraStanceRules.changes(7,false));
        assertFalse(SakuraStanceRules.changes(7,true));
        assertTrue(SakuraStanceRules.changes(8,true));
        assertFalse(SakuraStanceRules.changes(8,false));
        assertFalse(SakuraStanceRules.changes(0,false));
    }
    @Test void precommitCanceledOverwrittenAndStaleActionsCannotPassVerification() {
        assertTrue(SakuraStanceRules.committed(7,id("aerial_cleave"),100,100));
        assertTrue(SakuraStanceRules.committed(7,id("aerial_cleave_loop"),100,101));
        assertFalse(SakuraStanceRules.committed(7,id("aerial_cleave_loop"),100,103));
        assertFalse(SakuraStanceRules.committed(7,id("none"),100,100));
        assertFalse(SakuraStanceRules.committed(7,id("upperslash"),100,100));
        assertFalse(SakuraStanceRules.committed(8,id("upperslash"),100,99));
        assertTrue(SakuraStanceRules.committed(8,id("upperslash"),100,100));
        assertFalse(SakuraStanceRules.committed(0,id("combo_b1"),100,100));
    }
}
