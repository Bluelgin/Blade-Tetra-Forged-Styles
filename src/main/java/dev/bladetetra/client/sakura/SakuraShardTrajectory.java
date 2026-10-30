package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;

/** Original spatial curves inspired by the rhythm of Zenith, no Terraria code/assets. */
public final class SakuraShardTrajectory {
    public static final int LAUNCH_TICKS = 4, SLASH_TICKS = 7, RETURN_TICKS = 7;
    public static Vec3 cubic(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        t = Math.max(0, Math.min(1, t));
        double v = 1 - t;
        return a.scale(v*v*v).add(b.scale(3*v*v*t)).add(c.scale(3*v*t*t)).add(d.scale(t*t*t));
    }
    private static Vec3 planeRight(Vec3 forward) {
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        return side.lengthSqr() < .001 ? new Vec3(1, 0, 0) : side.normalize();
    }
    public static Vec3 orbit(Vec3 center, Vec3 forward, int index, int stage, double t) {
        double sign = ((index + stage) & 1) == 0 ? 1 : -1;
        double angle = index * 2.399963 + sign * t * Math.PI * 2;
        double tilt = (stage - 3.5) * .24 + (index % 3 - 1) * .28;
        Vec3 side = planeRight(forward), up = new Vec3(0, 1, 0);
        Vec3 axis = side.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 vertical = up.scale(Math.cos(tilt)).subtract(side.scale(Math.sin(tilt)));
        double radius = .62 + (index % 4) * .16 + (stage >= 5 ? .2 : 0);
        return center.add(axis.scale(Math.cos(angle) * radius))
                .add(vertical.scale(Math.sin(angle) * radius * .68))
                .add(forward.scale(Math.sin(angle * 2) * .34));
    }
    public static Vec3 launch(Vec3 start, Vec3 center, Vec3 forward, int index, int stage, double t) {
        Vec3 end = orbit(center, forward, index, stage, 0);
        Vec3 tangent = orbit(center, forward, index, stage, .001).subtract(end)
                .scale(LAUNCH_TICKS / (SLASH_TICKS * .001 * 3));
        return cubic(start, start.add(forward.scale(1.25)).add(0, .25, 0),
                end.subtract(tangent), end, t);
    }
    public static Vec3 returning(Vec3 start, Vec3 velocity, Vec3 home, double t) {
        // First derivative preserves the departure velocity, final derivative is
        // zero relative to the moving home. Fast players are followed continuously.
        return cubic(start, start.add(velocity.scale(RETURN_TICKS / 3.0)), home, home, t);
    }
    public static Vec3 approach(Vec3 from, Vec3 to, double maximum) {
        Vec3 delta = to.subtract(from);
        return delta.lengthSqr() > maximum * maximum ? from.add(delta.normalize().scale(maximum)) : to;
    }
    private SakuraShardTrajectory() {}
}
