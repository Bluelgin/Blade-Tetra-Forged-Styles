package dev.bladetetra.client;

/** Pure pose samples, in radians, shared by the single-sword rig and its transition tests. */
public final class MikageSingleSwordPose {
    public record Pose(float lean, float turn, float armX, float armY, float armZ,
                       float elbow, float wrist, float leftX, float leftY, float leftZ) {}
    public static final Pose IDLE = new Pose(0, -.08F, -.16F, -.04F, -.08F, -.10F, 0, 0, 0, .02F);
    public static final Pose READY = new Pose(.14F, -.38F, -.65F, -.70F, -.24F, -.32F, -.18F, -.16F, .08F, .16F);
    public static final Pose STRIKE = new Pose(.07F, .48F, -1.10F, .72F, -.35F, -.10F, .12F, -.12F, -.08F, .22F);
    public static final Pose HIGH = new Pose(-.08F, -.12F, -2.2F, -.20F, -.12F, -.30F, 0, -.25F, .10F, .20F);
    public static final Pose GUARD = new Pose(.06F, -.22F, -1.15F, -.75F, -.38F,
            -.45F, -.1F, -.65F, .25F, .3F);
    private MikageSingleSwordPose() {}

    public static Pose blend(Pose a, Pose b, float progress) {
        float t = Math.max(0, Math.min(1, progress));
        t = t * t * (3 - 2 * t);
        return new Pose(mix(a.lean,b.lean,t),mix(a.turn,b.turn,t),mix(a.armX,b.armX,t),
                mix(a.armY,b.armY,t),mix(a.armZ,b.armZ,t),mix(a.elbow,b.elbow,t),
                mix(a.wrist,b.wrist,t),mix(a.leftX,b.leftX,t),mix(a.leftY,b.leftY,t),mix(a.leftZ,b.leftZ,t));
    }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    public static Pose slash(float progress) {
        if (progress < .45F) return blend(READY, STRIKE, progress / .45F);
        if (progress < .65F) return STRIKE;
        return blend(STRIKE, IDLE, (progress - .65F) / .35F);
    }
    /** Match existing charge/release, impact (206), and descent (216), never sheath the sword. */
    public static Pose boundary(float ticks) {
        if (ticks < 32) return blend(IDLE, READY, ticks / 32);
        if (ticks < 194) return READY;
        if (ticks < 206) return blend(READY, STRIKE, (ticks - 194) / 12);
        if (ticks < 216) return STRIKE;
        return blend(STRIKE, IDLE, (ticks - 216) / 14);
    }
}
