package dev.bladetetra.client.awakening;

/** Blood moon hangs behind directional, depth-separated causal fractures. */
final class AkatsukiAwakeningVisual implements AwakeningVisualProfile {
    public double forward(){return 2.3;} public double height(){return 2.35;}
    public float angle(){return 90;}
    public void draw(RitualEffectBatch f) {
        float charge=f.window(20,45,85,100);
        // Moon is a fixed world plane; it never expands into a camera-covering billboard.
        f.plane(f.local(0,.3,.6),f.right.scale(1.3),new net.minecraft.world.phys.Vec3(0,1.3,0),19,charge*.68F);
        f.ring(f.local(0,.3,.65),1.35,true,16,charge*.65F,f.tick*.009F);
        for(int i=0;i<3;i++) {
            double a=(i-1)*Math.PI/3+.23;
            float show=f.window(45+i*10,48+i*10,81,92);
            double radius=1.6*(1-f.progress(80,90)*.94);
            var d=f.right.scale(Math.cos(a)*radius).add(0,Math.sin(a)*radius,0)
                    .add(f.forward.scale((i-1)*.55));
            f.line(f.center.subtract(d),f.center.add(d),.045,16,show);
            // Parallel narrow afterglow gives thickness without screen distortion shaders.
            f.line(f.center.subtract(d).add(0,.06,0),f.center.add(d).add(0,.06,0),.014,16,show*.4F);
        }
        float cut=f.window(85,87,90,98);
        for(int sign=-1;sign<=1;sign+=2)
            f.line(f.local(-2.3,sign*1.45,-.6),f.local(2.3,-sign*1.45,.6),.09,21,cut);
        f.common();
    }
}
