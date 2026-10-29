package dev.bladetetra.client.sakura;

import net.minecraft.world.phys.Vec3;

/** Six attack arrangements, independent of LOD count. No damage or entity simulation. */
public final class SakuraShardChoreography {
    private SakuraShardChoreography() {}

    static int wanted(int stage, int count) {
        return Math.max(stage == 6 ? 7 : 3, count / (stage <= 2 ? 6 : stage <= 4 ? 4 : stage == 5 ? 3 : 2));
    }

    static int candidate(int stage, int order, int count, int cursor) {
        int half = count / 2;
        if ((count & 1) != 0 && order == count - 1) return count - 1;
        // Opening wings really alternate sides. Crosses and finale draw from both.
        if (stage <= 2) {
            int side = (stage - 1 + order / half) % 2;
            return side * half + (order % half + cursor % half) % half;
        }
        return (order % 2) * half + (order / 2 + cursor % half) % half;
    }

    static double delay(int stage, int member, int members) {
        if (stage == 6) return member % 7; // seven interleaved waves, not one orbit
        return (member % (stage >= 3 ? 3 : 2)) * .35;
    }

    static double launchTicks(int stage) { return stage == 1 ? 6 : 2; }
    static double cutTicks(int stage) { return stage == 1 ? 3 : stage == 6 ? 1.5 : 4; }
    static double returnTicks(int stage) { return stage >= 5 ? 5 : 6; }

    static Vec3 cut(Vec3 center, Vec3 forward, int member, int stage, double t) {
        t = Math.max(0, Math.min(1, t));
        Vec3 right = new Vec3(forward.z, 0, -forward.x).normalize();
        if (right.lengthSqr() < .001) right = new Vec3(1, 0, 0);
        double side = (member & 1) == 0 ? 1 : -1;
        double sweep = 1 - 2 * t;
        double depth = Math.sin(t * Math.PI) * .35;
        return switch (stage) {
            case 1, 2 -> center.add(right.scale(sweep * (stage == 1 ? -1 : 1) * .95))
                    .add(0, sweep * .24, 0).add(forward.scale(depth));
            case 3, 4 -> center.add(right.scale(sweep * side * 1.1))
                    .add(0, sweep * (stage == 3 ? side : -side) * .8, 0)
                    .add(forward.scale(depth));
            case 5 -> center.add(right.scale(sweep * side * 1.35))
                    .add(0, Math.sin(t * Math.PI) * .25 + (member % 3 - 1) * .22, 0)
                    .add(forward.scale(sweep * .35));
            default -> center.add(right.scale(sweep * side * .95))
                    .add(0, sweep * (member % 3 - 1) * .7, 0)
                    .add(forward.scale(-sweep * .7));
        };
    }

    static Vec3 launch(Vec3 start, Vec3 center, Vec3 forward, int member, int stage, double t) {
        Vec3 end = cut(center, forward, member, stage, 0);
        Vec3 tangent = cut(center, forward, member, stage, .001).subtract(end)
                .scale(launchTicks(stage) / (cutTicks(stage) * .001 * 3));
        return SakuraShardTrajectory.cubic(start, start.add(forward.scale(.9)).add(0, .2, 0),
                end.subtract(tangent), end, t);
    }
}
