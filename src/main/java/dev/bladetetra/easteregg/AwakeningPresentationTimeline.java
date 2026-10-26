package dev.bladetetra.easteregg;

/**
 * Shared, deterministic time contract for a successful True Name Revelation.
 * All values are game ticks (20/s); no timer, render state or Minecraft client
 * classes belong in this model. World VFX and local HUD use the same zero beat.
 */
public final class AwakeningPresentationTimeline {
    public static final int WORLD_END = 44;
    public static final int OVERLAY_END = 60;
    public static final int NAME_START = 10;
    public static final int NAME_LOCK = 28;
    public static final int IMPACT = 32;
    public static final int FADE_START = 49;

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
        return smooth(tick / 8.0F)
                * (1.0F - smooth((tick - FADE_START) / 11.0F));
    }

    private AwakeningPresentationTimeline() {}
}
