package dev.bladetetra.easteregg;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraShardAttackRulesTest {
    @Test void pulseCountsRespectThePerStageBudgetIncludingAllSevenFinaleWaves() {
        for(int stage=1;stage<=6;stage++) {
            int count=0;
            for(int tick=0;tick<20;tick++) if(SakuraShardAttackRules.pulse(stage,tick)) count++;
            assertEquals(SakuraShardAttackRules.pulseCount(stage),count);
            assertEquals(8.,count*(8./count),1e-9);
        }
    }
    @Test void damageUsesConfirmedEffectiveHitAndHasFiniteSingleShotCap() {
        assertEquals(2.5,SakuraShardAttackRules.damage(10,.25,6));
        assertEquals(6,SakuraShardAttackRules.damage(10000,.25,6));
        assertEquals(0,SakuraShardAttackRules.damage(Float.NaN,.25,6));
        assertEquals(0,SakuraShardAttackRules.damage(-1,.25,6));
    }
    @Test void viewSpacePlatformsAlternateOutsideTheCrosshairAndFollowLookAndEye() {
        Vec3 eye=new Vec3(10,30,20),look=new Vec3(0,0,1);
        Vec3 left=SakuraShardAttackRules.platform(eye,look,false);
        Vec3 right=SakuraShardAttackRules.platform(eye,look,true);
        assertEquals(-.95,left.x-eye.x,1e-9);assertEquals(.95,right.x-eye.x,1e-9);
        assertEquals(.65,left.y-eye.y,1e-9);assertEquals(.3,left.z-eye.z,1e-9);
        Vec3 turned=SakuraShardAttackRules.platform(eye,new Vec3(1,0,0),true);
        assertEquals(-.95,turned.z-eye.z,1e-9);assertEquals(.3,turned.x-eye.x,1e-9);
        for(Vec3 vertical:new Vec3[]{new Vec3(0,1,0),new Vec3(0,-1,0)})
            assertTrue(Double.isFinite(SakuraShardAttackRules.platform(eye,vertical,true).x));
    }
    @Test void allLogicalIndicesMapToDistinctStableHomesAtEveryLod() {
        for(int count:new int[]{24,48,72}) {
            var used=new java.util.HashSet<Integer>();
            for(int i=0;i<24;i++) assertTrue(used.add(SakuraShardAttackRules.index(i,count)));
            assertEquals(24,used.size());
        }
    }
    @Test void saSchedulingWaitsForAllNativeB1AndRushCallbacks() {
        assertEquals(15,SakuraFlowRules.minimumAutoTick(1));
        for(int stage=2;stage<=6;stage++) assertEquals(7,SakuraFlowRules.minimumAutoTick(stage));
    }
}
