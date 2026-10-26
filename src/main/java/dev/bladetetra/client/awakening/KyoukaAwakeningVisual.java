package dev.bladetetra.client.awakening;

import net.minecraft.world.phys.Vec3;
/** Pseudo water reflection and local mirror geometry; no real-time reflection target. */
final class KyoukaAwakeningVisual implements AwakeningVisualProfile {
    public double forward(){return 1.55;} public double height(){return 2.15;}
    public float angle(){return -12;}
    public void draw(RitualEffectBatch f) {
        float water=f.window(10,30,95,112);
        f.plane(f.root.add(0,.045,0),new Vec3(1.7,0,0),new Vec3(0,0,1.7),0,water*.36F);
        f.ring(f.root.add(0,.05,0),1.65,false,3,water*.6F,f.tick*.012F);
        int mirrors=f.quality==0?2:4;
        for(int i=0;i<mirrors;i++) {
            double a=i*Math.PI*2/mirrors;
            Vec3 at=f.center.add(Math.cos(a)*1.5,-.2,Math.sin(a)*1.5);
            Vec3 side=new Vec3(-Math.sin(a)*.45,0,Math.cos(a)*.45);
            float show=f.window(40+i*5,46+i*5,85,91);
            f.plane(at,side,new Vec3(0,.85,0),0,show*.38F);
            float slash=f.window(52+i*6,54+i*6,58+i*6,61+i*6);
            f.line(at.subtract(side).add(0,-.4,0),at.add(side).add(0,.5,0),.04,3,slash);
        }
        if(f.tick>=85) {
            int count=f.quality==0?12:32;
            float spread=f.progress(85,110);
            for(int i=0;i<count;i++) {
                double a=i*2.399963229728653;
                f.tile(f.local(Math.cos(a)*(.4+spread*2.5),Math.sin(a*.7)*spread,
                        Math.sin(a)*(.4+spread*2.5)),.13,5+i%4,(1-spread)*.8F,(float)a+spread);
            }
        }
        f.common();
    }
}
