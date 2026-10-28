package dev.bladetetra.kyouka;

/** Pure limits: one budget for the entire three-blade formation. */
public final class KyoukaRules {
    public static final int MAX_WINDOWS = 64;
    public static float budget(double nativeDamage) {
        return Double.isFinite(nativeDamage) && nativeDamage > 0 && nativeDamage <= 1024
                ? (float) (nativeDamage * .75) : 0;
    }
    public static boolean canStart(long points, long unit, int grade, int cost) {
        return unit > 0 && grade >= 4 && points / (double) unit >= 4 && points >= cost && cost >= 0;
    }
    public static boolean sustain(long points, long unit, int grade) {
        return unit > 0 && grade >= 3 && points / (double) unit >= 3;
    }
    public static double motionTime(double elapsedMs, double start, double end,
            double maximum, double speed, boolean loop) {
        double span = Math.min(Math.abs(end-start), maximum);
        if (!Double.isFinite(elapsedMs) || !Double.isFinite(speed) || speed <= 0 || span <= 0) return start;
        double time = Math.max(0, elapsedMs) * speed;
        time = loop ? time % span : Math.min(span, time);
        return start + Math.signum(end-start) * time;
    }
    private KyoukaRules() {}
}
