package dev.bladetetra.client;

import java.util.Arrays;

/** Presentation time only: no gameplay state or inferred combo triggers. Times are in game ticks. */
public final class SoulHudTimeline {
    public static final int HOLD_TICKS = 60, PREVIEW_TICKS = 40, FADE_TICKS = 8;
    private final double[] until = new double[4], pulses = new double[4];
    private int active;
    private double previewUntil;

    public SoulHudTimeline() { reset(); }
    public void reset() {
        Arrays.fill(until, Double.NEGATIVE_INFINITY);
        Arrays.fill(pulses, Double.NEGATIVE_INFINITY);
        active = 0; previewUntil = Double.NEGATIVE_INFINITY;
    }
    public void update(double now, int triggered, int sustained) {
        for (int i = 0; i < 4; i++) {
            int bit = 1 << i;
            if ((triggered & bit) != 0 || ((sustained & ~active) & bit) != 0) {
                until[i] = now + HOLD_TICKS; pulses[i] = now;
            }
            if (((active & ~sustained) & bit) != 0) until[i] = now + HOLD_TICKS;
        }
        active = sustained;
    }
    public void preview(double now, int selected) {
        previewUntil = now + PREVIEW_TICKS;
        if (selected >= 1 && selected <= 4) pulses[selected - 1] = now;
    }
    public float alpha(int soul, double now) {
        if (soul < 1 || soul > 4) return 0;
        return (active & (1 << (soul - 1))) != 0 ? 1 : fade(Math.max(until[soul - 1], previewUntil), now);
    }
    public float previewAlpha(double now) { return fade(previewUntil, now); }
    public double pulseAge(int soul, double now) { return now - pulses[soul - 1]; }
    private static float fade(double until, double now) {
        return (float)Math.max(0, Math.min(1, (until + FADE_TICKS - now) / FADE_TICKS));
    }
}
