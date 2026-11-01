package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraPartialFractureTest {
    @Test void fourStagesReuseExactPoolAndConserveBladeAndBackFormation() {
        var frame=new SakuraShardFormation.Frame(Vec3.ZERO,0,1.62);
        var pool=new SakuraShardPool(48,frame,0);
        pool.initialFraction(1);
        var identities=pool.shards.clone();
        Vec3 grip=new Vec3(.4,1,0),tip=grip.add(0,1,0);
        for(int stage=1;stage<=4;stage++) {
            double start=stage*10;
            pool.partial(1-(stage-1)/4F,1-stage/4F,start,grip,tip);
            for(int t=0;t<=6;t++) pool.tick(frame,start+t,grip,tip);
            int visible=0;
            for(int i=0;i<48;i++) {
                assertSame(identities[i],pool.shards[i]);
                if(pool.shards[i].visibility>.99) visible++;
                assertNull(pool.shards[i].group);
                assertTrue(Double.isFinite(pool.shards[i].position.x));
            }
            assertEquals(stage*12,visible);
            assertEquals(1-stage/4F,pool.partialFraction(start+6));
        }
        pool.beginStance(false,60,grip,tip,0);
        for(int t=60;t<=80;t++) pool.tick(frame,t,grip,tip);
        for(var shard:pool.shards) assertEquals(0,shard.visibility);
    }
}
