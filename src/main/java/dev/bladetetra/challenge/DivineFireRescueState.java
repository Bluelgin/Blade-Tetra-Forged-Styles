package dev.bladetetra.challenge;

/** A finite, non-revivable rescue budget for one participant in one ritual. */
public final class DivineFireRescueState {
    public static final int DURATION = 120;
    public static final double RADIUS = 2.5;
    private boolean used;
    private long started;

    public boolean tryStart(float health, float maximum, long now) {
        if (used || !Float.isFinite(health) || !Float.isFinite(maximum)
                || maximum <= 0 || health <= 0 || health > maximum * .3F) return false;
        used = true;
        started = now;
        return true;
    }
    public boolean active(long now) { return used && now >= started && now - started < DURATION; }
    public int remaining(long now) { return active(now) ? (int) (DURATION - (now - started)) : 0; }
    public boolean used() { return used; }
    public boolean healingPulse(long now) { return active(now) && (now - started) % 20 == 0; }
    public static float healingAmount(float maximum) { return Math.max(0, Math.min(12, maximum * .6F) / 6); }
}
