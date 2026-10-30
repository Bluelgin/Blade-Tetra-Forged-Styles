package dev.bladetetra.client.sakura;

/** Client-only transition clock. No persistent state and no combat gating. */
public record SakuraStanceTransition(boolean dispersing, double startedAt, float fromFraction) {
    public static final int DISPERSE_TICKS = 16, CONDENSE_TICKS = 14;
    public int duration() { return dispersing ? DISPERSE_TICKS : CONDENSE_TICKS; }
    public boolean active(double now) { return now - startedAt < duration(); }
    public float fraction(double now) {
        double age = now - startedAt;
        double t = dispersing ? (age - 2) / 8 : (age - 2) / 10;
        t = smooth(t);
        return (float)(fromFraction + ((dispersing ? 0 : 1) - fromFraction) * t);
    }
    public static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t*t*(3-2*t); }
}
