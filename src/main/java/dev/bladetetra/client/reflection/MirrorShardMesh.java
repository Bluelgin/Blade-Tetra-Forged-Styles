package dev.bladetetra.client.reflection;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** A shared triangulation: every seam used by the crack pass becomes an actual shard edge. */
public final class MirrorShardMesh {
    public record UV(float u,float v) {}
    public record Shard(int id,UV a,UV b,UV c) {
        public float u() { return (a.u+b.u+c.u)/3; }
        public float v() { return (a.v+b.v+c.v)/3; }
    }
    private static final List<Shard> LOW=create(4,4), MEDIUM=create(6,6), HIGH=create(8,8);
    public static List<Shard> shards(int quality) { return quality<=1?LOW:quality==2?MEDIUM:HIGH; }
    private static List<Shard> create(int columns,int rows) {
        UV[][] points=new UV[columns+1][rows+1];
        for(int x=0;x<=columns;x++) for(int y=0;y<=rows;y++) {
            float u=-1+2f*x/columns,v=-1+2f*y/rows;
            if(x>0&&x<columns&&y>0&&y<rows&&!(x==columns/2&&y==rows/2)) {
                u+=Math.sin(x*17+y*31)*.3/columns;v+=Math.cos(x*23-y*11)*.3/rows;
            }
            points[x][y]=new UV(u,v);
        }
        List<Shard> out=new ArrayList<>();
        for(int x=0;x<columns;x++) for(int y=0;y<rows;y++) {
            var a=points[x][y];var b=points[x+1][y];var c=points[x+1][y+1];var d=points[x][y+1];
            out.add(new Shard(out.size(),a,b,c));out.add(new Shard(out.size(),a,c,d));
        }
        return List.copyOf(out);
    }
    /** Vertex identity/UV never changes as a shard moves or rejoins the blade. */
    public static Vec3 position(MirrorPlane plane,Shard s,UV uv,float scatter,float gather,Vec3 blade) {
        Vec3 center=plane.point(s.u(),s.v()),p=plane.point(uv.u,uv.v);
        double angle=scatter*(.8+(s.id%9)*.19);
        Vec3 local=p.subtract(center);
        // Rodrigues rotation about a stable world-space axis gives physical tumbling, not billboards.
        Vec3 axis=plane.right().scale(Math.sin(s.id*2.4)).add(plane.up().scale(Math.cos(s.id*1.7)))
                .add(plane.normal().scale(.4)).normalize();
        Vec3 rotated=local.scale(Math.cos(angle)).add(axis.cross(local).scale(Math.sin(angle)))
                .add(axis.scale(axis.dot(local)*(1-Math.cos(angle))));
        Vec3 drift=plane.right().scale(s.u()*scatter*.85).add(plane.up().scale(s.v()*scatter*.6))
                .add(plane.normal().scale(Math.sin(s.id*2.4)*scatter*.5));
        Vec3 spread=center.add(drift).add(rotated);
        double orbit=s.id*2.39996+gather*6;
        Vec3 destination=blade.add(plane.right().scale(Math.cos(orbit)*.22*(1-gather)))
                .add(plane.normal().scale(Math.sin(orbit)*.22*(1-gather))).add(0,.3*s.v()*(1-gather),0);
        return spread.lerp(destination,gather);
    }
    private MirrorShardMesh() {}
}
