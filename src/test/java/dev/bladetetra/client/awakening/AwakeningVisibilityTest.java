package dev.bladetetra.client.awakening;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AwakeningVisibilityTest {
    @Test void onlyActivePreparedHeldBladeIsSuppressed() {
        for(String t:new String[]{"blade","blade_damaged","blade_luminous","blade_damaged_luminous"}) {
            assertTrue(AwakeningVisibility.hide(true,true,true,false,t));
            assertFalse(AwakeningVisibility.hide(false,true,true,false,t));
            assertFalse(AwakeningVisibility.hide(true,false,true,false,t));
            assertFalse(AwakeningVisibility.hide(true,true,false,false,t));
            assertFalse(AwakeningVisibility.hide(true,true,true,true,t));
        }
        for(String t:new String[]{"item_blade","item_damaged","item_bladens","base","color","effect","sheath","sheath_luminous"})
            assertFalse(AwakeningVisibility.hide(true,true,true,false,t));
    }
    @Test void mountedSheathNeverParticipatesInNativeSuppression() {
        for (String target : new String[]{"sheath", "sheath_luminous"}) {
            assertTrue(AwakeningVisibility.isSheathTarget(target));
            assertFalse(AwakeningVisibility.isBladeTarget(target));
            for (int flags = 0; flags < 16; flags++)
                assertFalse(AwakeningVisibility.hide((flags&1)!=0,(flags&2)!=0,
                        (flags&4)!=0,(flags&8)!=0,target));
        }
        assertFalse(AwakeningVisibility.isSheathTarget("blade"));
        assertFalse(AwakeningVisibility.isSheathTarget("item_blade"));
    }
}
