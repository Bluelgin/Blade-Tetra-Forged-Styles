package dev.bladetetra.client.awakening;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;
import static dev.bladetetra.easteregg.AwakeningPresentationTimeline.*;

/** Deterministic mirror geometry. No world capture or GPU resources. */
public final class MirrorRitualController {
    public enum Phase { FORM, ENCLOSE, CRACK, BURST, ABSORB, END }
    public record Facet(int index, Vec3 a, Vec3 b, Vec3 c) {
        public Vec3 center() { return a.add(b).add(c).scale(1.0/3); }
    }
    public static final List<Facet> FACETS=makeFacets();
    public static Phase phase(float tick) {
        return tick<54?Phase.FORM:tick<72?Phase.ENCLOSE:tick<85?Phase.CRACK:
                tick<97?Phase.BURST:tick<112?Phase.ABSORB:Phase.END;
    }
    public static boolean bladeVisible(float tick) { return tick<56||tick>=85; }
    public static float crack(float tick) { return smooth(progress(tick,72,85)); }
    public static float gather(float tick,int index) {
        return smooth(progress(tick,97+index%3,109+index%3));
    }
    public static float formation(float tick,int index) {
        return smooth(progress(tick,24+index%6*2,44+index%6*2));
    }
    public static Vec3 bladeOffset(float tick,Vec3 forward) {
        double enter=.25*smooth(progress(tick,40,56))*(1-smooth(progress(tick,85,91)));
        double burst=-.85*smooth(progress(tick,85,91))*(1-smooth(progress(tick,100,112)));
        return forward.scale(enter+burst);
    }
    public static float bladeYaw(float tick) {
        return -55*smooth(progress(tick,36,56))*(1-smooth(progress(tick,85,92)));
    }
    /** The exact assembled triangle becomes the flying shard at tick 85. */
    public static Vec3 vertex(Facet f,Vec3 vertex,float tick,Vec3 blade) {
        Vec3 center=f.center();
        float formed=formation(tick,f.index());
        float burst=smooth(progress(tick,85,94));
        float absorb=gather(tick,f.index());
        double angle=f.index()*2.3999632297;
        Vec3 scatter=new Vec3(Math.cos(angle)*(.5+center.length()*.6),
                Math.sin(angle)*.65+.12,-.35-(f.index()%4)*.12).scale(burst);
        Vec3 point=center.add(scatter);
        // Three curved streams converge on the moving native blade.
        double stream=f.index()%3*Math.PI*2/3;
        Vec3 curve=new Vec3(Math.cos(stream+absorb*3)*.55,
                Math.sin(stream+absorb*3)*.55,-.3).scale(Math.sin(absorb*Math.PI));
        point=point.lerp(blade,absorb).add(curve);
        Vec3 delta=vertex.subtract(center).yRot((float)(burst*Math.sin(angle)*1.6))
                .zRot((float)(burst*Math.cos(angle)*1.1)).scale(formed*(1-absorb));
        return point.add(delta).add(center.normalize().scale((1-formed)*.65));
    }
    private static List<Facet> makeFacets() {
        var result=new ArrayList<Facet>();
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            Vec3 innerA=point(a,.43),innerB=point(b,.43),outerA=point(a,1),outerB=point(b,1);
            result.add(new Facet(result.size(),Vec3.ZERO,innerA,innerB));
            result.add(new Facet(result.size(),innerA,outerA,outerB));
            result.add(new Facet(result.size(),innerA,outerB,innerB));
        }
        return List.copyOf(result);
    }
    private static Vec3 point(double angle,double radius) {
        return new Vec3(Math.cos(angle)*radius*.95,Math.sin(angle)*radius*1.3,radius*radius*.22);
    }
    private MirrorRitualController() {}
}
