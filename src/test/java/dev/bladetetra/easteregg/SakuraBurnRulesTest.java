package dev.bladetetra.easteregg;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SakuraBurnRulesTest {
    @Test void burnIsMonotoneBoundedAndCompleteBeforeReleaseLock() {
        for(long points:new long[]{0,1,120,1000,6000}) {
            int release=SakuraRecallSequence.burnRelease(points,120);
            assertTrue(release>=32&&release<=128);
            long previous=0;
            for(int age=0;age<=release;age++) {
                long due=SakuraRecallSequence.burnDue(points,age,release);
                assertTrue(due>=previous&&due<=points);previous=due;
            }
            assertEquals(points,SakuraRecallSequence.burnDue(points,release-SakuraRecallSequence.LOCK,release));
        }
        assertTrue(SakuraRecallSequence.burnRelease(6000,120)>SakuraRecallSequence.burnRelease(120,120));
    }
}
