package dev.bladetetra.client.awakening;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.bladetetra.client.vfx.render.AwakenedAtlasBatch;
import dev.bladetetra.client.vfx.render.VfxPrimitives;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Thin budgeted view into the existing four atlas batches, not another renderer. */
public final class RitualEffectBatch {
    public final float tick, alpha;
    public final int quality, soul;
    public final Vec3 center, root, eye, forward, right;
    private final BufferBuilder buffer;
    private final Matrix4f matrix;
    private final int[] remaining;
    RitualEffectBatch(BufferBuilder buffer, Matrix4f matrix, Vec3 eye,
            AwakeningSession s, float partial, float intensity, int quality, int[] remaining) {
        this.buffer=buffer; this.matrix=matrix; this.eye=eye; this.quality=quality;
        this.remaining=remaining; soul=s.data.soul();
        tick=s.age((net.minecraft.client.multiplayer.ClientLevel)s.owner.level(),partial);
        center=FloatingBladeRenderer.position(s,partial,tick);
        root=FloatingBladeRenderer.root(s,partial);
        forward=FloatingBladeRenderer.forward(s,partial); right=new Vec3(forward.z,0,-forward.x);
        alpha=Math.min(1,intensity)*AwakeningPresentationTimeline.smooth(tick/8)
                *(1-AwakeningPresentationTimeline.smooth((tick-110)/10));
    }
    public float progress(float from,float to) { return AwakeningPresentationTimeline.progress(tick,from,to); }
    public float window(float start,float rise,float fall,float end) {
        return AwakeningPresentationTimeline.smooth(progress(start,rise))
                *(1-AwakeningPresentationTimeline.smooth(progress(fall,end)));
    }
    public Vec3 local(double x,double y,double z) {
        return center.add(right.scale(x)).add(0,y,0).add(forward.scale(z));
    }
    private boolean reserve() { if (remaining[0]<=0) return false; --remaining[0]; return true; }
    public void tile(Vec3 at,double size,int tile,float opacity,float rotation) {
        if (!reserve()) return;
        AwakenedAtlasBatch.billboardTile(buffer,matrix,eye,at,size,tile,soul,alpha*opacity,rotation);
    }
    public void line(Vec3 a,Vec3 b,double width,int tile,float opacity) {
        if (!reserve()) return;
        AwakenedAtlasBatch.segment(buffer,matrix,eye,a,b,width,tile,soul,alpha*opacity);
    }
    public void plane(Vec3 at,Vec3 x,Vec3 y,int tile,float opacity) {
        if (!reserve()) return;
        AwakenedAtlasBatch.uvQuad(buffer,matrix,at.subtract(x).add(y),at.subtract(x).subtract(y),
                at.add(x).subtract(y),at.add(x).add(y),tile,soul,alpha*opacity);
    }
    public void ring(Vec3 at,double radius,boolean vertical,int tile,float opacity,float phase) {
        int segments=quality==0?16:32;
        for(int i=0;i<segments;i++) {
            double a=phase+i*Math.PI*2/segments,b=phase+(i+1)*Math.PI*2/segments;
            Vec3 p=vertical?at.add(right.scale(Math.cos(a)*radius)).add(0,Math.sin(a)*radius,0)
                    :at.add(Math.cos(a)*radius,0,Math.sin(a)*radius);
            Vec3 q=vertical?at.add(right.scale(Math.cos(b)*radius)).add(0,Math.sin(b)*radius,0)
                    :at.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            line(p,q,.025,tile,opacity);
        }
    }
    public void common() {
        // Deliberate low-density orbit and a short continuous return trail.
        float orbit=window(12,25,85,102);
        for(int i=0;i<(quality==0?4:8);i++) {
            double a=tick*.045+i*Math.PI/4;
            tile(local(Math.cos(a)*.65,Math.sin(a*2)*.2,Math.sin(a)*.65),.09,
                    soul==1?18:soul==2?5:soul==3?5:4,orbit*.5F,(float)a);
        }
        if(tick>=100) {
            int pieces=quality==0?3:7;
            for(int i=1;i<=pieces;i++) {
                // Sample the same flight curve, do not invent an unrelated straight beam.
                float previous=Math.max(100,tick-i*.65F);
                float f=AwakeningPresentationTimeline.flight(previous,soul);
                float now=AwakeningPresentationTimeline.flight(tick,soul);
                Vec3 tail=center.add(forward.scale((f-now)*1.5)).add(0,(f-now)*1.4,0);
                line(center,tail,.018,soul==1?16:soul==2?3:soul==3?1:4,.3F/i);
            }
        }
        int seal=soul==1?23:soul==2?12:soul==3?7:9;
        tile(local(0,.7,.1),1.15,seal,window(78,85,96,104),0);
    }
}
