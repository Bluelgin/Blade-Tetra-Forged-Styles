package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraShardPoolTest {
    private static final SakuraShardFormation.Frame FRAME=new SakuraShardFormation.Frame(Vec3.ZERO,0,1.62);
    private static final Vec3 TARGET=new Vec3(0,1.3,3.5);

    @Test void departingIndicesCreateRealGapsAndReturnToTheirOriginalHomes() {
        var pool=new SakuraShardPool(48,FRAME,0);
        var identities=pool.shards.clone();
        assertTrue(pool.launch(1,1,0,0,TARGET,-1,false));
        assertTrue(pool.idleCount()<48);
        for(int t=1;t<=40;t++) pool.tick(FRAME,t);
        assertEquals(48,pool.idleCount());
        for(int i=0;i<48;i++) {
            assertSame(identities[i],pool.shards[i]);
            assertEquals(SakuraShardFormation.home(FRAME,i,48,40),pool.shards[i].position);
        }
        for(var group:pool.groups) assertNull(group);
    }
    @Test void saturatedContinuousCombosCannotAllocateUnboundedGroupsOrShards() {
        var pool=new SakuraShardPool(72,FRAME,0);
        for(int t=0;t<600;t++) {
            if(t%4==0) pool.launch(t/4%6+1,t+1,t,t,TARGET,-1,false);
            pool.tick(FRAME,t);
            assertEquals(72,pool.shards.length);
            int groups=0;
            for(var group:pool.groups) if(group!=null) groups++;
            assertTrue(groups<=6);
            for(var shard:pool.shards) {
                assertTrue(Double.isFinite(shard.position.x));
                assertTrue(shard.position.distanceToSqr(TARGET)<100);
                assertTrue(shard.history<=SakuraShardPool.HISTORY);
            }
        }
        for(int t=600;t<640;t++) pool.tick(FRAME,t);
        assertEquals(72,pool.idleCount());
    }
    @Test void predictionsAndDuplicateAuthorityUseOneDepartureAndOneHitFlash() {
        var pool=new SakuraShardPool(48,FRAME,100);
        assertTrue(pool.launch(2,-1,100,100,TARGET,-1,true));
        int idle=pool.idleCount();
        assertTrue(pool.launch(2,7,101,102,TARGET,12,false));
        assertEquals(idle,pool.idleCount());
        assertFalse(pool.launch(2,7,101,102,TARGET,12,false));
        assertTrue(pool.confirm(7,TARGET,12,103));
        assertFalse(pool.confirm(7,TARGET,12,104));
        assertEquals(idle,pool.idleCount());
        assertFalse(pool.confirm(999,TARGET,12,104));
    }
    @Test void authorityBeforeLocalPredictionDoesNotDuplicate() {
        var pool=new SakuraShardPool(24,FRAME,0);
        assertTrue(pool.launch(1,10,0,0,TARGET,-1,false));
        int idle=pool.idleCount();
        assertTrue(pool.launch(1,-1,0,1,TARGET,-1,true));
        assertEquals(idle,pool.idleCount());
    }
    @Test void deadOrUnloadedTargetReturnsTheExistingBladesWithoutTeleporting() {
        var pool=new SakuraShardPool(48,FRAME,0);
        pool.launch(5,1,0,0,TARGET,9,false);
        for(int t=0;t<7;t++) pool.tick(FRAME,t);
        for(var group:pool.groups) if(group!=null) group.canceled=true;
        for(int t=7;t<=25;t++) {
            pool.tick(FRAME,t);
            for(var shard:pool.shards) assertTrue(shard.position.distanceToSqr(shard.previous)<36);
        }
        assertEquals(48,pool.idleCount());
    }
    @Test void correctionsAreRateLimitedAndMissesHaveNoConfirmation() {
        assertEquals(new Vec3(.55,0,0),SakuraShardTrajectory.approach(Vec3.ZERO,new Vec3(9,0,0),.55));
        var pool=new SakuraShardPool(24,FRAME,0);
        pool.launch(4,1,0,0,TARGET,-1,false);
        for(var group:pool.groups) if(group!=null) assertFalse(group.confirmed);
    }
}
