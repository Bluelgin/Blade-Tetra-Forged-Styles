package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakenedEffectQueueTest {
    @Test void transientFloodCannotEvictAnchorsAndRemovalStillWorks() {
        var queue=new AwakenedEffectQueue<String>();
        queue.offer("sword",42,12);
        for(int i=0;i<1000;i++) queue.offer("flash"+i,null,12);
        assertEquals(13,queue.size());assertTrue(queue.contains("sword"));
        queue.offer("remaining-life",42,12);
        assertEquals(13,queue.size());assertFalse(queue.contains("sword"));
        assertTrue(queue.removeIf("remaining-life"::equals));assertEquals(12,queue.size());
        queue.clear();assertTrue(queue.isEmpty());
    }
    @Test void anchorsHaveHardBoundAndTickExpiryCanRemoveThem() {
        var queue=new AwakenedEffectQueue<Integer>();
        for(int i=0;i<600;i++) queue.offer(i,i,12);
        assertEquals(512,queue.size());
        var it=queue.iterator();while(it.hasNext()) { it.next();it.remove(); }
        assertTrue(queue.isEmpty());
    }
}
