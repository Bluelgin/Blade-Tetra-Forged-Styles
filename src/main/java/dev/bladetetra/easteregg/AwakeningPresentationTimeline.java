package dev.bladetetra.easteregg;

/**
 * Shared, deterministic time contract for a successful True Name Revelation.
 * All values are game ticks (20/s); no timer, render state or Minecraft client
 * classes belong in this model. World VFX and local HUD use the same zero beat.
 */
public final class AwakeningPresentationTimeline {
    public static final int WORLD_END = 120;
    public static final int OVERLAY_END = 120;
    public static final int NAME_START = 78;
    public static final int NAME_LOCK = 85;
    public static final int IMPACT = 85;
    public static final int FADE_START = 100;

    public static final int DEPART_END = 20;
    public static final int HOVER_END = 45;
    public static final int MANIFEST_END = 85;
    public static final int RELEASE_END = 100;
    public enum Phase { DEPART, HOVER, MANIFEST, RELEASE, RETURN, COMPLETE }

    public static Phase phase(float tick) {
        if (tick < DEPART_END) return Phase.DEPART;
        if (tick < HOVER_END) return Phase.HOVER;
        if (tick < MANIFEST_END) return Phase.MANIFEST;
        if (tick < RELEASE_END) return Phase.RELEASE;
        if (tick < WORLD_END) return Phase.RETURN;
        return Phase.COMPLETE;
    }

    public static float progress(float tick, float from, float to) {
        return Math.max(0, Math.min(1, (tick - from) / (to - from)));
    }

    public static float easeIn(float t) { t = progress(t, 0, 1); return t * t * t; }
    public static float easeOut(float t) { return 1 - easeIn(1 - t); }
    public static float bezier(float t, float a, float b, float c, float d) {
        t = progress(t, 0, 1); float u = 1 - t;
        return u*u*u*a + 3*u*u*t*b + 3*u*t*t*c + t*t*t*d;
    }

    /** 0 at the hand, 1 at the altar; continuous at all stage boundaries. */
    public static float flight(float tick, int soul) {
        if (tick < DEPART_END) {
            float t = progress(tick, 0, DEPART_END);
            return soul == 4 ? easeOut(t) : smooth(t);
        }
        // Thunder holds its breath before an extremely fast, continuous return.
        float start = soul == 4 ? 113 : RELEASE_END;
        return 1 - easeIn(progress(tick, start, WORLD_END));
    }

    public static float bladeFraction(float tick, int soul) {
        if (soul != 3) return 1;
        return 1 - smooth(progress(tick, HOVER_END, 65))
                + smooth(progress(tick, MANIFEST_END, RELEASE_END));
    }

    public static boolean validSoul(int soul) {
        return soul >= 1 && soul <= 4;
    }

    public static String nameKey(int soul) {
        return switch (soul) {
            case 1 -> "vfx.blade_tetra.true_name.akatsuki";
            case 2 -> "vfx.blade_tetra.true_name.kyouka";
            case 3 -> "vfx.blade_tetra.true_name.senbonzakura";
            case 4 -> "vfx.blade_tetra.true_name.raikiri";
            default -> "";
        };
    }

    public static int tint(int soul) {
        return switch (soul) {
            case 1 -> 0xF96579;
            case 2 -> 0xB1E7FF;
            case 3 -> 0xFFB8E6;
            case 4 -> 0xD6F5FF;
            default -> 0xFFFFFF;
        };
    }

    public static float smooth(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float fade(int tick) {
        return smooth((tick - NAME_START) / 7.0F)
                * (1.0F - smooth((tick - FADE_START) / 20.0F));
    }

    private AwakeningPresentationTimeline() {}
}
