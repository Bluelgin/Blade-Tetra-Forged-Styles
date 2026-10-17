package dev.bladetetra.challenge.mikage;

/** One release, three passes. Native Combo B completion is an event, never an invented timer. */
public final class GateCorridorSequence {
    public enum Stage { DEPLOY, CUE, APPROACH, COMBO, EXIT, INTERVAL, LAND, DOWN, COMPLETE }
    public static final int PASSES = 3, HIT_LIMIT = 2, HIT_INTERVAL = 10;
    private Stage stage = Stage.DEPLOY;
    private int remaining = 24, pass, hits;
    private long lastHit = Long.MIN_VALUE;
    public Stage stage() { return stage; }
    public int pass() { return pass; }
    public boolean attacking() { return stage == Stage.COMBO; }
    public void tick() {
        if (remaining > 0 && --remaining == 0) switch (stage) {
            case DEPLOY, INTERVAL -> change(Stage.CUE, 16 - pass * 2);
            case CUE -> change(Stage.APPROACH, 0);
            default -> { }
        }
    }
    public void approachComplete() { require(Stage.APPROACH); change(Stage.COMBO, 0); }
    public void comboComplete() { require(Stage.COMBO); change(Stage.EXIT, 0); }
    public void exitComplete() {
        require(Stage.EXIT);
        if (++pass == PASSES) change(Stage.LAND, 0);
        else { hits = 0; lastHit = Long.MIN_VALUE; change(Stage.INTERVAL, 10); }
    }
    public boolean parry() {
        if (!attacking()) return false;
        change(Stage.DOWN, 0); return true;
    }
    public boolean mayHit(long tick) {
        return attacking() && hits < HIT_LIMIT && (lastHit == Long.MIN_VALUE || tick - lastHit >= HIT_INTERVAL);
    }
    public void hit(long tick) {
        if (!mayHit(tick)) throw new IllegalStateException("Invalid corridor contact");
        hits++; lastHit = tick;
    }
    public void landed() {
        if (stage != Stage.LAND && stage != Stage.DOWN) throw new IllegalStateException("Not descending");
        change(Stage.COMPLETE, 0);
    }
    private void require(Stage expected) {
        if (stage != expected) throw new IllegalStateException("Expected " + expected + ", got " + stage);
    }
    private void change(Stage next, int ticks) { stage = next; remaining = ticks; }
}
