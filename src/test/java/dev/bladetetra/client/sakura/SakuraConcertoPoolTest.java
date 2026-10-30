package dev.bladetetra.client.sakura;

import dev.bladetetra.easteregg.SakuraShardAttackRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraConcertoPoolTest {
    private static final SakuraShardFormation.Frame FRAME=new SakuraShardFormation.Frame(Vec3.ZERO,0,1.62);
    private static final Vec3 TARGET=new Vec3(0,1.3,3.5);
    @Test void oneRealShardDepartsAStableSlotForAnEnemyAnchorAndReturns() {
        var pool=new SakuraShardPool(48,FRAME,0);
        var identities=pool.shards.clone();
        assertTrue(pool.concerto(0,false,1,0,0,TARGET,5));
        assertEquals(47,pool.idleCount());
        for(int t=0;t<=8;t++) pool.tick(FRAME,t);
        assertEquals(TARGET,pool.shards[0].position);
        assertEquals(new Vec3(0,-1,0),pool.shards[0].direction);
        assertTrue(pool.concerto(1,true,2,5,5,TARGET,5));
        assertEquals(46,pool.idleCount());
        for(int t=9;t<30;t++) pool.tick(FRAME,t);
        assertEquals(48,pool.idleCount());
        for(int i=0;i<48;i++) {
            assertSame(identities[i],pool.shards[i]);
            assertEquals(SakuraShardFormation.home(FRAME,i,48,29),pool.shards[i].position);
        }
    }
    @Test void busyIndicesAndDuplicatePacketsNeverCreateAnotherShard() {
        var pool=new SakuraShardPool(24,FRAME,0);
        assertTrue(pool.concerto(0,false,1,0,0,TARGET,5));
        assertFalse(pool.concerto(0,true,2,1,1,TARGET,5));
        assertFalse(pool.concerto(1,true,1,0,0,TARGET,5));
        assertEquals(23,pool.idleCount());
        pool.recall();
        for(int t=0;t<15;t++) pool.tick(FRAME,t);
        assertEquals(24,pool.idleCount());
    }
    @Test void enemyAnchorStaysIndependentOfOwnerYawPitchAndJump() {
        var pool=new SakuraShardPool(72,FRAME,0);
        pool.concerto(5,true,1,0,0,TARGET,5);
        var moved=new SakuraShardFormation.Frame(new Vec3(2,3,4),Math.PI/2,1.62,.3);
        for(int t=0;t<=8;t++) pool.tick(moved,t);
        Vec3 look=SakuraShardFormation.forward(moved).scale(Math.cos(.3)).add(0,-Math.sin(.3),0);
        int index=SakuraShardAttackRules.index(5,72);
        assertEquals(TARGET,pool.shards[index].position);
        pool.recall();for(int t=9;t<30;t++) pool.tick(moved,t);
        assertEquals(SakuraShardFormation.home(moved,index,72,29),pool.shards[index].position);
    }
}
