package dev.bladetetra.easteregg;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AwakeningSessionLifecycleTest {
    @Test void deathSwitchDimensionOwnerAndTimeoutReleaseTheVisualLease() {
        var player=UUID.randomUUID();
        var key=new AwakeningSessionKey(player,"blade-a","overworld",2,100);
        assertTrue(key.live(player,"blade-a","overworld",2,true,100));
        assertTrue(key.live(player,"blade-a","overworld",2,true,219));
        assertFalse(key.live(player,"blade-a","overworld",2,true,220));
        assertFalse(key.live(player,"blade-a","overworld",2,true,99));
        assertFalse(key.live(player,"blade-a","overworld",2,false,150));
        assertFalse(key.live(player,"blade-b","overworld",2,true,150));
        assertFalse(key.live(player,"blade-a","nether",2,true,150));
        assertFalse(key.live(player,"blade-a","overworld",3,true,150));
        assertFalse(key.live(UUID.randomUUID(),"blade-a","overworld",2,true,150));
    }
}
