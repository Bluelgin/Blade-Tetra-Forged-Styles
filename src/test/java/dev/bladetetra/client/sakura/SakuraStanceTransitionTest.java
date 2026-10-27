package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraStanceTransitionTest {
    private static final SakuraShardFormation.Frame FRAME=new SakuraShardFormation.Frame(Vec3.ZERO,0,1.62);
    private static final Vec3 GRIP=new Vec3(.3,1,.3),TIP=new Vec3(.3,2,.3);
    @Test void bladeDoesNotDisappearAtCommitAndRestoresFromHiltToTip() {
        var down=new SakuraStanceTransition(true,100,1);
        assertEquals(1,down.fraction(100));assertEquals(1,down.fraction(102));
        assertEquals(0,down.fraction(110));
        var up=new SakuraStanceTransition(false,100,0);
        float previous=0;
        for(double t=100;t<=114;t+=.25) {
            assertTrue(up.fraction(t)>=previous);previous=up.fraction(t);
        }
        assertEquals(1,previous);
        var reversal=new SakuraStanceTransition(true,104,up.fraction(104));
        assertEquals(up.fraction(104),reversal.fraction(104));
    }
    @Test void downUsesSameIndicesAndBuildsTheOriginalFormationContinuously() {
        var pool=new SakuraShardPool(48,FRAME,0);var identities=pool.shards.clone();
        pool.hide();pool.beginStance(true,0,GRIP,TIP,1);
        for(int t=0;t<=20;t++) {
            pool.tick(FRAME,t,GRIP,TIP);
            for(var s:pool.shards) assertTrue(s.position.distanceToSqr(s.previous)<16);
        }
        for(int i=0;i<48;i++) {
            assertSame(identities[i],pool.shards[i]);assertEquals(1,pool.shards[i].visibility);
            assertEquals(SakuraShardFormation.home(FRAME,i,48,20),pool.shards[i].position);
        }
    }
    @Test void upperRecallsFlyingAndIdleShardsWithoutReplacingOrTeleportingThem() {
        var pool=new SakuraShardPool(48,FRAME,0);var identities=pool.shards.clone();
        pool.launch(6,1,0,0,new Vec3(0,1,4),-1,false);
        for(int t=0;t<=6;t++) pool.tick(FRAME,t);
        Vec3[] positions=java.util.Arrays.stream(pool.shards).map(s->s.position).toArray(Vec3[]::new);
        pool.beginStance(false,6,GRIP,TIP,0);
        for(int i=0;i<48;i++) assertEquals(positions[i],pool.shards[i].position);
        assertFalse(pool.launch(1,2,6,6,new Vec3(0,1,4),-1,false));
        for(int t=6;t<=20;t++) {
            pool.tick(FRAME,t,GRIP,TIP);
            for(var s:pool.shards) assertTrue(s.position.distanceToSqr(s.previous)<25);
        }
        for(int i=0;i<48;i++) {
            assertSame(identities[i],pool.shards[i]);assertEquals(0,pool.shards[i].visibility);
            assertTrue(pool.shards[i].position.distanceToSqr(GRIP)<1.1);
        }
    }
    @Test void earlyFollowupCanUseAlreadyVisibleFormingFragments() {
        var pool=new SakuraShardPool(48,FRAME,0);pool.hide();pool.beginStance(true,0,GRIP,TIP,1);
        for(int t=0;t<=6;t++) pool.tick(FRAME,t,GRIP,TIP);
        assertTrue(pool.launch(1,3,6,6,new Vec3(0,1,4),-1,false));
        assertEquals(48,pool.shards.length);
    }
}
