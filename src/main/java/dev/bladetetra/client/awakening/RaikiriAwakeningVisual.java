package dev.bladetetra.client.awakening;

import net.minecraft.world.phys.Vec3;
/** Stateless seeded lightning ribbons; never creates a LightningBolt entity. */
final class RaikiriAwakeningVisual implements AwakeningVisualProfile {
    public double forward(){return 1.35;} public double height(){return 2.85;}
    public float angle(){return -90;}
    public void draw(RitualEffectBatch f) {
        float charge=f.window(10,40,82,91);
        int arcs=f.quality==0?3:6;
        for(int i=0;i<arcs;i++) {
            double a=i*Math.PI*2/arcs+Math.floor(f.tick/3)*.17;
            Vec3 from=f.local(Math.cos(a)*1.1,-.5+Math.sin(a),Math.sin(a)*1.1);
            bolt(f,from,f.center,8,.018,charge*.8F,i);
        }
        float cloud=f.window(35,65,86,100);
        f.ring(f.center.add(0,2.0,0),.8,false,4,cloud*.6F,f.tick*.035F);
        f.ring(f.center.add(0,2.1,0),1.15,false,4,cloud*.4F,-f.tick*.025F);
        float strike=f.window(85,86,88,94);
        if(strike>0) {
            bolt(f,f.center.add(0,7,0),f.center,18,.13,strike,19);
            bolt(f,f.center.add(0,7,0),f.center,18,.035,strike,19);
            f.tile(f.center,1.4,8,strike,0);
        }
        float ground=f.window(87,89,101,116);
        f.ring(f.root.add(0,.06,0),.7+f.progress(87,116)*1.5,false,4,ground,.0F);
        // An intentional quiet hold from 100..113 precedes the seven-tick return.
        f.common();
    }
    private void bolt(RitualEffectBatch f,Vec3 start,Vec3 end,int segments,
            double width,float alpha,int seed) {
        if(alpha<=0) return;
        Vec3 last=start;
        for(int i=1;i<=segments;i++) {
            double t=(double)i/segments,envelope=Math.sin(t*Math.PI)*.17;
            double phase=seed*13.37+i*7.13+Math.floor(f.tick/2)*3.71;
            Vec3 next=start.lerp(end,t).add(Math.sin(phase)*envelope,0,Math.cos(phase)*envelope);
            f.line(last,next,width,4,alpha); last=next;
        }
    }
}
