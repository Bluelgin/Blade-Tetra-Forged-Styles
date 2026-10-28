package dev.bladetetra.client.sakura;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.client.vfx.render.AwakenedAtlasBatch;
import dev.bladetetra.client.vfx.render.VfxPrimitives;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Appends all shards to the existing Sakura atlas batch: no entities or per-shard draws. */
public final class SakuraShardRenderer {
    // Centre of an opaque white highlight texel in #40's original Sakura tile 4.
    private static final float WHITE_U=63.5F/512, WHITE_V=161.5F/512;
    private static final Vec3 UP=new Vec3(0,1,0);
    public static int draw(BufferBuilder buffer,Matrix4f matrix,Vec3 eye,SakuraShardPool pool,
            float partial,float intensity,int quality,int budget) {
        float baseAlpha=Math.min(1,intensity)*.84F;
        boolean near=quality>0&&pool.shards[0].position.distanceToSqr(eye)<24*24;
        for(var shard:pool.shards) {
            if(budget<=0) break;
            float alpha=baseAlpha*(shard.previousVisibility+(shard.visibility-shard.previousVisibility)*partial);
            if(shard.group!=null&&shard.group.concerto&&shard.state==SakuraShardPool.State.SLASH)
                alpha=Math.min(1,alpha*1.2F);
            if(alpha<=.001F) continue;
            Vec3 center=shard.previous.lerp(shard.position,partial);
            Vec3 direction=shard.flying()?shard.direction:new Vec3(
                    Math.sin((shard.index+.5)*Math.PI/pool.shards.length-Math.PI/2)*.35,1,.10).normalize();
            Vec3 side=direction.cross(UP);
            if(side.lengthSqr()<.01) side=direction.cross(new Vec3(0,0,1));
            side=side.normalize();
            Vec3 normal=side.cross(direction).normalize();
            double size=shard.flying()?.45+(shard.index%4)*.06:.28+(shard.index%4)*.035;
            if(near&&budget>=6) {
                mesh(buffer,matrix,center,direction,side,normal,size,alpha);
                budget-=6;
            } else {
                // Far LOD retains the trajectory-aligned plane; never faces the camera.
                Vec3 along=direction.scale(size*.5), across=side.scale(size*.27);
                AwakenedAtlasBatch.uvQuad(buffer,matrix,center.subtract(across).add(along),
                        center.subtract(across).subtract(along),center.add(across).subtract(along),
                        center.add(across).add(along),shard.index%3,3,alpha);
                budget--;
            }
            if(!shard.flying()) continue;
            int history=Math.min(shard.history,quality==0?2:quality==1?4:6);
            Vec3 from=center;
            for(int i=0;i<history&&budget>0;i++) {
                Vec3 to=shard.trail[i];
                if(to==null) break;
                Vec3 delta=from.subtract(to);
                if(delta.lengthSqr()<.0001||delta.lengthSqr()>16) {from=to;continue;}
                // A narrow swept ribbon in the blade's own plane, not a laser beam.
                Vec3 width=side.scale(.022*(1-(double)i/history));
                solid(buffer,matrix,from.add(width),to.add(width),to.subtract(width),from.subtract(width),
                        alpha*.32F*(1-(float)i/history),0xFFD1E6);
                budget--; from=to;
            }
        }
        // Only server-confirmed hits add a brief thin cross; misses have no hit flash.
        for(var group:pool.groups) {
            if(group==null||!group.confirmed) continue;
            double age=pool.time()+partial-group.confirmedAt;
            if(age<0||age>=3||budget<2) continue;
            float a=(float)((1-age/3)*.48)*baseAlpha;
            Vec3 x=new Vec3(.22,.14,0), y=new Vec3(-.22,.14,0), w=new Vec3(0,.012,.012);
            solid(buffer,matrix,group.hitPosition.subtract(x).add(w),group.hitPosition.add(x).add(w),
                    group.hitPosition.add(x).subtract(w),group.hitPosition.subtract(x).subtract(w),a,0xFFF6FB);
            solid(buffer,matrix,group.hitPosition.subtract(y).add(w),group.hitPosition.add(y).add(w),
                    group.hitPosition.add(y).subtract(w),group.hitPosition.subtract(y).subtract(w),a,0xFFF6FB);
            budget-=2;
        }
        return budget;
    }
    private static void mesh(BufferBuilder b,Matrix4f m,Vec3 p,Vec3 d,Vec3 s,Vec3 n,double size,float alpha) {
        Vec3 tip=p.add(d.scale(size*.62)), base=p.subtract(d.scale(size*.38));
        Vec3 left=p.add(s.scale(size*.13)), right=p.subtract(s.scale(size*.13));
        Vec3 thick=n.scale(.008);
        solid(b,m,tip.add(thick),left.add(thick),base.add(thick),right.add(thick),alpha,0xFFD0E3);
        solid(b,m,tip.subtract(thick),right.subtract(thick),base.subtract(thick),left.subtract(thick),alpha,0xBA7392);
        solid(b,m,tip.add(thick),tip.subtract(thick),left.subtract(thick),left.add(thick),alpha,0xFFF5FA);
        solid(b,m,left.add(thick),left.subtract(thick),base.subtract(thick),base.add(thick),alpha,0xFCEAF2);
        solid(b,m,base.add(thick),base.subtract(thick),right.subtract(thick),right.add(thick),alpha,0xD792B0);
        solid(b,m,right.add(thick),right.subtract(thick),tip.subtract(thick),tip.add(thick),alpha,0xFFFFFF);
    }
    private static void solid(BufferBuilder b,Matrix4f m,Vec3 a,Vec3 c,Vec3 d,Vec3 e,float alpha,int color) {
        VfxPrimitives.texturedQuad(b,m,a,c,d,e,WHITE_U,WHITE_V,WHITE_U,WHITE_V,alpha,color);
    }
    private SakuraShardRenderer() {}
}
