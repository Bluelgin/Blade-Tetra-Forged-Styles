package dev.bladetetra.client.awakening;

/** Deterministic batched fragments; the physical snapshot reforms before return. */
final class SenbonzakuraAwakeningVisual implements AwakeningVisualProfile {
    public double forward(){return 2.2;} public double height(){return 2.1;}
    public float angle(){return 0;}
    public void draw(RitualEffectBatch f) {
        float breakUp=f.progress(45,65),gather=f.progress(85,100);
        if(f.tick>=40&&f.tick<100) {
            int count=f.quality==0?36:f.quality==1?96:192;
            double spread=breakUp*(1-gather);
            for(int i=0;i<count;i++) {
                int layer=i%3;
                double a=i*2.399963229728653+f.tick*(.035+layer*.018)+breakUp*breakUp*3;
                double radius=(.7+layer*.42)*spread;
                var shard=f.local(Math.cos(a)*radius,
                        Math.sin(a*2+layer)*(.18+layer*.12)*spread+(layer-1)*.22*spread,
                        Math.sin(a)*radius);
                float show=f.window(40,55,95,100);
                f.tile(shard,.12+(i%4)*.018,i%3,show*.9F,(float)a);
                if(f.quality>0&&i%3==0)
                    f.line(shard,f.local(Math.cos(a-.09)*radius,Math.sin((a-.09)*2+layer)*(.18+layer*.12)*spread
                            +(layer-1)*.22*spread,Math.sin(a-.09)*radius),.012,1,show*.38F);
            }
        }
        // Fine fracture lines travel from the real blade tip toward its guard.
        float cracks=f.window(25,40,50,64);
        for(int i=0;i<5;i++)
            f.line(f.local(-.75+i*.13,.04,0),f.local(-.69+i*.13,-.04,.03),.012,1,cracks);
        f.common();
    }
}
