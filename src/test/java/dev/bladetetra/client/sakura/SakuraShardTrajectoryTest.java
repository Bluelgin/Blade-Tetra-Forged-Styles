package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraShardTrajectoryTest {
    @Test void launchOrbitAndReturnHaveContinuousEndpointsAndCurvature() {
        Vec3 start=new Vec3(-1,1,-1),target=new Vec3(0,1,4),forward=new Vec3(0,0,1);
        for(int stage=1;stage<=6;stage++) for(int index=0;index<72;index++) {
            assertEquals(start,SakuraShardTrajectory.launch(start,target,forward,index,stage,0));
            Vec3 join=SakuraShardTrajectory.orbit(target,forward,index,stage,0);
            assertTrue(join.distanceToSqr(SakuraShardTrajectory.launch(start,target,forward,index,stage,1))<1e-20);
            Vec3 middle=SakuraShardTrajectory.launch(start,target,forward,index,stage,.5);
            assertTrue(middle.distanceToSqr(start.lerp(join,.5))>.001);
            Vec3 orbitEnd=SakuraShardTrajectory.orbit(target,forward,index,stage,1);
            assertTrue(orbitEnd.distanceToSqr(join)<1e-20);
            assertEquals(start,SakuraShardTrajectory.returning(orbitEnd,forward,start,1));
            assertEquals(orbitEnd,SakuraShardTrajectory.returning(orbitEnd,forward,start,0));
        }
    }
    @Test void crescentYawWrapUsesTheShortArcInBothDirections() {
        for(int direction:new int[]{-1,1}) {
            var a=new SakuraShardFormation.Frame(Vec3.ZERO,Math.toRadians(179*direction),1.62);
            var b=new SakuraShardFormation.Frame(Vec3.ZERO,Math.toRadians(-179*direction),1.62);
            var midpoint=a.interpolate(b,.5);
            assertTrue(Math.abs(Math.abs(midpoint.yaw())-Math.PI)<1e-10);
            for(int i=0;i<48;i++) {
                Vec3 from=SakuraShardFormation.home(a,i,48,0);
                Vec3 to=SakuraShardFormation.home(b,i,48,0);
                assertTrue(from.distanceToSqr(to)<.01);
            }
        }
    }
    @Test void extremePitchFallbackBasisStaysFinite() {
        for(double t=0;t<=1;t+=.05) {
            Vec3 p=SakuraShardTrajectory.orbit(Vec3.ZERO,new Vec3(0,1,0),1,6,t);
            assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z));
        }
    }
}
