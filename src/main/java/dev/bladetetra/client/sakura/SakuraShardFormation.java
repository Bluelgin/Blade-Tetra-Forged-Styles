package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;

/** Stable indexed homes in a body-relative crescent, never camera billboards. */
public final class SakuraShardFormation {
    public record Frame(Vec3 root, double yaw, double height) {
        public Frame interpolate(Frame to, double t) {
            return new Frame(root.lerp(to.root, t), yaw + wrap(to.yaw - yaw) * t,
                    height + (to.height - height) * t);
        }
    }
    public static double wrap(double radians) {
        return Math.atan2(Math.sin(radians), Math.cos(radians));
    }
    public static Vec3 forward(Frame frame) { return new Vec3(-Math.sin(frame.yaw), 0, Math.cos(frame.yaw)); }
    public static Vec3 home(Frame frame, int index, int count, double time) {
        double angle = -Math.PI / 2 + Math.PI * (index + .5) / count;
        double drift = Math.sin(time * .07 + index * 1.618) * .055;
        Vec3 forward = forward(frame), right = new Vec3(forward.z, 0, -forward.x);
        return frame.root.add(0, frame.height * .72, 0)
                .subtract(forward.scale(.9 + .07 * (index % 3)))
                .add(right.scale(Math.sin(angle) * (1.65 + drift)))
                .add(0, Math.cos(angle) * .65 + (index % 4 - 1.5) * .075, 0);
    }
    private SakuraShardFormation() {}
}
