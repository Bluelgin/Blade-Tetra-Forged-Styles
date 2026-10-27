package dev.bladetetra.client.reflection;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MirrorShardMeshTest {
    @Test void tessellationCoversWholeMirrorWithoutOverlapsOrMissingArea() {
        for(int q=1;q<=3;q++) {
            double area=0;
            for(var s:MirrorShardMesh.shards(q)) {
                double cross=(s.b().u()-s.a().u())*(s.c().v()-s.a().v())-(s.b().v()-s.a().v())*(s.c().u()-s.a().u());
                assertTrue(cross>0);area+=cross*.5;
                for(var p:new MirrorShardMesh.UV[]{s.a(),s.b(),s.c()}) {assertTrue(Math.abs(p.u())<=1);assertTrue(Math.abs(p.v())<=1);}
            }
            assertEquals(4,area,1e-5);
        }
    }
    @Test void originalUvRemainsStableWhileSameShardTravelsAndRejoins() {
        var p=new MirrorPlane(new Vec3(1,2,3),new Vec3(1,0,0),new Vec3(0,1,0),2,3);
        var blade=new Vec3(4,5,6);
        for(var s:MirrorShardMesh.shards(3)) {
            assertEquals(p.point(s.a().u(),s.a().v()),MirrorShardMesh.position(p,s,s.a(),0,0,blade));
            assertEquals(0,MirrorShardMesh.position(p,s,s.a(),1,1,blade).distanceTo(blade),1e-10);
            assertTrue(Double.isFinite(MirrorShardMesh.position(p,s,s.b(),.7f,.3f,blade).x));
        }
        assertSame(MirrorShardMesh.shards(2),MirrorShardMesh.shards(2));
    }
    @Test void updateBudgetDoesNotDependOnFrameRateAndCompatibilityNeverCaptures() {
        var q=ReflectionQuality.of(2);
        assertTrue(q.due(1,0));assertFalse(q.due(10_000_000L,1));assertTrue(q.due(100_000_000L,1));
        assertFalse(ReflectionQuality.of(0).due(Long.MAX_VALUE,0));
        assertTrue(MirrorShardMesh.shards(3).size()<=128);
    }
}
