package dev.bladetetra.client.awakening;

import dev.bladetetra.client.reflection.MirrorPlane;
import net.minecraft.world.phys.Vec3;
import static dev.bladetetra.easteregg.AwakeningPresentationTimeline.*;

/** Pure presentation state, on the existing 120-tick ritual clock. No GL, entities or timers. */
public final class MirrorRitualController {
    public enum Phase { BIRTH, REFLECTIONS, CRACK, SHATTER, RELEASE, END }
    public record Frame(float tick, Phase phase, MirrorPlane plane, float opacity, float crack,
                        float scatter, float gather) {
        public boolean broken() { return tick>=85; }
        public boolean live() { return tick>=12&&tick<112; }
    }
    public static Frame frame(float tick,Vec3 root,Vec3 forward,double eyeHeight) {
        float open=smooth(progress(tick,12,32));
        double turn=Math.sin(progress(Math.min(tick,85),32,85)*Math.PI)*.22;
        Vec3 side=new Vec3(forward.z,0,-forward.x);
        Vec3 right=side.scale(Math.cos(turn)).add(forward.scale(Math.sin(turn))).normalize();
        Vec3 center=root.add(forward.scale(3.05)).add(0,eyeHeight+.35,0);
        var plane=new MirrorPlane(center,right,new Vec3(0,1,0),1.4*Math.max(.001,open),1.65*Math.max(.001,open));
        Phase phase=tick<32?Phase.BIRTH:tick<72?Phase.REFLECTIONS:tick<85?Phase.CRACK:
                tick<100?Phase.SHATTER:tick<112?Phase.RELEASE:Phase.END;
        return new Frame(tick,phase,plane,open*(1-smooth(progress(tick,105,112))),
                smooth(progress(tick,72,85)),smooth(progress(tick,88,100)),smooth(progress(tick,100,110)));
    }
    public static Vec3 bladeOffset(float tick,Vec3 forward) {
        return forward.scale(.65*smooth(progress(tick,66,77))*(1-smooth(progress(tick,85,98))));
    }
    public static float bladeYaw(float tick) {
        return -90*smooth(progress(tick,56,70))*(1-smooth(progress(tick,90,103)));
    }
    private MirrorRitualController() {}
}
