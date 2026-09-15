package dev.bladetetra.visual;

/** Bounded presentation values only; never used by the erosion/damage engine. */
public final class DeadThoughtVisualMath {
    public static final int LIFETIME = 44;
    public static final int MAX_SCENES = 8;
    public static final int MAX_SCARS = 12;

    public static int severity(double erosion) {
        if (Double.isNaN(erosion) || erosion < .15) return 0;
        if (erosion < .40) return 1;
        if (erosion < .70) return 2;
        return 3;
    }

    public static float scale(double width, double height) {
        double size = Math.max(width, height);
        return Double.isFinite(size) ? (float) Math.max(.8, Math.min(2.2, size / 2)) : 1;
    }

    public static float wheelRadius(float scale) {
        return Math.max(1.5F, Math.min(3.5F, 1.6F * scale));
    }

    public static float ramp(float age, float start, float duration) {
        return Math.max(0, Math.min(1, (age - start) / duration));
    }

    public static float opening(float age) {
        if (age < 0 || age >= 8) return 0;
        return Math.min(ramp(age, 0, 2), 1 - ramp(age, 4, 4));
    }

    private DeadThoughtVisualMath() {}
}
