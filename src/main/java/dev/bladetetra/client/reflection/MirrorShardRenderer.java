package dev.bladetetra.client.reflection;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** One frozen reflection for all shards, plus a single silver side/back batch. Never captures worlds. */
public final class MirrorShardRenderer {
    public static void draw(ReflectionRenderer reflection,MirrorPlane plane,Matrix4f view,Vec3 eye,
            int quality,float scatter,float gather,Vec3 blade,float alpha) {
        var shards=MirrorShardMesh.shards(quality);
        boolean textured=MirrorSurfaceRenderer.available()&&reflection!=null&&reflection.ready();
        if(textured) MirrorSurfaceRenderer.textured(reflection,b->{
            for(var s:shards) for(var uv:new MirrorShardMesh.UV[]{s.a(),s.b(),s.c()}) {
                var p=MirrorShardMesh.position(plane,s,uv,scatter,gather,blade).subtract(eye);
                MirrorSurfaceRenderer.vertex(b,view,p,uv.u(),uv.v(),alpha*(1-gather));
            }
        });
        MirrorSurfaceRenderer.solid(b->{
            for(var s:shards) {
                Vec3 a=MirrorShardMesh.position(plane,s,s.a(),scatter,gather,blade);
                Vec3 c=MirrorShardMesh.position(plane,s,s.b(),scatter,gather,blade);
                Vec3 d=MirrorShardMesh.position(plane,s,s.c(),scatter,gather,blade);
                Vec3 normal=c.subtract(a).cross(d.subtract(a)).normalize().scale(.012);
                Vec3 backA=a.subtract(normal),backC=c.subtract(normal),backD=d.subtract(normal);
                MirrorSurfaceRenderer.triangle(b,view,eye,backA,backC,backD,0x48565E,alpha*(1-gather));
                if(!textured) MirrorSurfaceRenderer.triangle(b,view,eye,a,c,d,0x9CACB8,alpha*(1-gather));
                Vec3[] front={a,c,d},back={backA,backC,backD};
                for(int i=0;i<3;i++) {
                    int next=(i+1)%3;
                    MirrorSurfaceRenderer.triangle(b,view,eye,front[i],back[i],back[next],0xDBE8F2,alpha*(1-gather));
                    MirrorSurfaceRenderer.triangle(b,view,eye,front[i],back[next],front[next],0xDBE8F2,alpha*(1-gather));
                }
                MirrorSurfaceRenderer.edge(b,view,eye,a,c,.005,0xE6EFF6,alpha*(1-gather));
                MirrorSurfaceRenderer.edge(b,view,eye,c,d,.005,0xE6EFF6,alpha*(1-gather));
                MirrorSurfaceRenderer.edge(b,view,eye,d,a,.005,0xE6EFF6,alpha*(1-gather));
                if(gather>0) MirrorSurfaceRenderer.edge(b,view,eye,a,a.lerp(blade,.16),.008,0xF4F9FF,alpha*gather*(1-gather));
            }
        });
    }
    private MirrorShardRenderer() {}
}
