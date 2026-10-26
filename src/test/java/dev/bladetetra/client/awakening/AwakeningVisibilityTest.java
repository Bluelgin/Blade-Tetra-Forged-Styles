package dev.bladetetra.client.awakening;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AwakeningVisibilityTest {
    @Test void onlyActivePreparedHeldBladeAndSheathAreSuppressed() {
        for(String t:new String[]{"blade","blade_damaged","blade_luminous","sheath","sheath_luminous"}) {
            assertTrue(AwakeningVisibility.hide(true,true,true,false,t));
            assertFalse(AwakeningVisibility.hide(false,true,true,false,t));
            assertFalse(AwakeningVisibility.hide(true,false,true,false,t));
            assertFalse(AwakeningVisibility.hide(true,true,false,false,t));
            assertFalse(AwakeningVisibility.hide(true,true,true,true,t));
        }
        for(String t:new String[]{"item_blade","item_damaged","item_bladens","base","color","effect"})
            assertFalse(AwakeningVisibility.hide(true,true,true,false,t));
    }
}
