package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.client.vfx.render.AwakenedAtlasBatch;
import dev.bladetetra.client.vfx.render.VfxPrimitives;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Atlas-batched segmented beams and forks, with no extra world scan, emitter, or render pass. */
final class RaikiriBurstVisual {
    static void draw(BufferBuilder b,Matrix4f m,Vec3 eye,AwakenedBladeVfxPacket data,
            float age,float alpha,int quality) {
        Vec3 center=new Vec3(data.x(),data.y(),data.z());
        int count=data.variant();
        if(data.type()==AwakenedBladeVfxPacket.NEEDLE_CHARGE) {
            double radius=.15+.65*(1-Math.min(1,age/3));
            ring(b,m,eye,center,radius,.035,alpha*.8F,quality==0?12:20);
            beam(b,m,eye,center.add(0,-.55,0),center.add(0,.85,0),.025,alpha);
            AwakenedAtlasBatch.billboardTile(b,m,eye,center,.6,6,4,alpha,age*.2F);
            Vec3 end=new Vec3(data.endX(),data.endY(),data.endZ());
            if(end.distanceToSqr(center)>.01) arc(b,m,eye,center,end,alpha,quality,3.7*count+center.x,.025);
            return;
        }
        double radius=Math.min(4,Math.abs(data.endX()-data.x()));
        double expansion=1-Math.pow(1-Math.min(1,age/8),3);
        int sections=quality==0?16:quality==1?24:32;
        ring(b,m,eye,center.add(0,-.28,0),radius*expansion,.035+.012*count,alpha*.85F,sections);
        if(quality>0) ring(b,m,eye,center.add(0,-.24,0),radius*expansion*.78,.016,alpha*.45F,sections);
        float flash=alpha*(float)Math.max(0,1-age/4);
        AwakenedAtlasBatch.billboardTile(b,m,eye,center,1.6+count*.2,6,4,flash,0);
        if(age>=9) return;
        int spokes=quality==0?4:quality==1?6:8;
        for(int i=0;i<spokes;i++) {
            double angle=i*Math.PI*2/spokes+center.x*.13+center.z*.19;
            double length=radius*(.65+.08*(i%3))*(.65+.35*expansion);
            Vec3 end=center.add(Math.cos(angle)*length,.1+.3*(i%3),Math.sin(angle)*length);
            arc(b,m,eye,center,end,alpha*(float)(1-age/11),quality,angle+count*4,.04+.008*count);
        }
        if(quality>0) arc(b,m,eye,center,center.add(.12,1.2+count*.25,-.08),alpha*.7F,quality,center.z,.035);
    }
    private static void arc(BufferBuilder b,Matrix4f m,Vec3 eye,Vec3 from,Vec3 to,
            float alpha,int quality,double seed,double width) {
        int sections=quality==0?4:7;
        Vec3 direction=to.subtract(from).normalize();
        Vec3 side=direction.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<.01) side=new Vec3(1,0,0);
        side=side.normalize(); Vec3 up=direction.cross(side).normalize();
        Vec3 previous=from;
        for(int i=1;i<=sections;i++) {
            double t=i/(double)sections;
            double envelope=Math.sin(t*Math.PI);
            Vec3 next=from.lerp(to,t).add(side.scale(Math.sin(seed+i*7.3)*.25*envelope))
                    .add(up.scale(Math.cos(seed+i*4.1)*.18*envelope));
            beam(b,m,eye,previous,next,width*(1-.55*t),alpha);
            if(quality>0 && i==sections/2) {
                Vec3 tip=next.add(side.scale(.3+seed%1*.15)).add(0,.25,0);
                beam(b,m,eye,next,tip,width*.5,alpha*.65F);
            }
            previous=next;
        }
    }
    private static void ring(BufferBuilder b,Matrix4f m,Vec3 eye,Vec3 center,double radius,
            double width,float alpha,int sections) {
        Vec3 previous=center.add(radius,0,0);
        for(int i=1;i<=sections;i++) {
            double angle=i*Math.PI*2/sections;
            Vec3 next=center.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            beam(b,m,eye,previous,next,width,alpha);previous=next;
        }
    }
    private static void beam(BufferBuilder b,Matrix4f m,Vec3 eye,Vec3 from,Vec3 to,double width,float alpha) {
        Vec3 side=to.subtract(from).cross(eye.subtract(from));
        if(side.lengthSqr()<1e-10) return;
        side=side.normalize();
        strip(b,m,from,to,side.scale(width*3),alpha*.18F,0x4AAEFF);
        strip(b,m,from,to,side.scale(width),alpha*.75F,0x9BEAFF);
        strip(b,m,from,to,side.scale(width*.35),alpha,0xF0FDFF);
    }
    private static void strip(BufferBuilder b,Matrix4f m,Vec3 from,Vec3 to,Vec3 side,float alpha,int color) {
        // Solid tile 2 in the existing original atlas; all three layers share one batch.
        VfxPrimitives.texturedQuad(b,m,from.add(side),from.subtract(side),to.subtract(side),to.add(side),
                256.5F/512,.5F/512,383.5F/512,127.5F/512,alpha,color);
    }
    private RaikiriBurstVisual() {}
}
