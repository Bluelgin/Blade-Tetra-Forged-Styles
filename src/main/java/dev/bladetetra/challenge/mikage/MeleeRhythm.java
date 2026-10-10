package dev.bladetetra.challenge.mikage;

/** One authored phrase: fresh contacts per beat, bounded pursuit and a real ending. */
public final class MeleeRhythm {
    public static final int CONTACT_TICKS = 4, PARRY_PAUSE = 2, MAX_PURSUITS = 2;
    public static final int FINISHER_OPENING = 28, HIT_RECOVERY = 12;
    private final MikageMove move;
    private final int[] releases;
    private int age, beat = -1, pause, pursuits, recovery;
    private int pursuitBeat = -2;
    private boolean beatParried, hit, countered;

    public MeleeRhythm(MikageMove move) {
        if (!move.melee()) throw new IllegalArgumentException("Melee phrase required");
        this.move = move; releases = move.releases();
    }
    /** False holds the pose for a two-tick blade contact; the beat clock does not drift. */
    public boolean tick() {
        if (hit) { if (recovery > 0) recovery--; return false; }
        if (countered) return false;
        if (pause > 0) { pause--; return false; }
        age++; return true;
    }
    public int age() { return age; }
    public int beat() { return beat; }
    public boolean windingUp() { return !hit && !countered && age < move.windup; }
    public int untilNextBeat() {
        return beat + 1 < releases.length ? move.windup + releases[beat + 1] - age : Integer.MAX_VALUE;
    }
    public boolean releaseDue() { return !hit && !countered && untilNextBeat() == 0; }
    public int release() {
        if (!releaseDue()) throw new IllegalStateException("Not on a beat");
        beat++; beatParried = false; return beat;
    }
    public boolean mayContact(int bornBeat) {
        return bornBeat == beat && !hit && !countered && !beatParried;
    }
    /** Returns true only when this beat's first parry is accepted. */
    public boolean parry(int bornBeat) {
        if (!mayContact(bornBeat)) return false;
        beatParried = true;
        if (beat == releases.length - 1) countered = true;
        else pause = PARRY_PAUSE;
        return true;
    }
    public void hit(int bornBeat) {
        if (!mayContact(bornBeat)) return;
        hit = true; recovery = HIT_RECOVERY;
    }
    public boolean beginPursuit(boolean retreating, double distance) {
        if (hit || countered || pause > 0 || beat < 0 || untilNextBeat() != 6
                || pursuitBeat == beat || !retreating || distance <= 3.5 || pursuits >= MAX_PURSUITS) return false;
        pursuitBeat = beat; pursuits++; return true;
    }
    public int pursuits() { return pursuits; }
    public boolean hit() { return hit; }
    public boolean countered() { return countered; }
    public boolean complete() {
        return hit ? recovery == 0 : countered
                || age >= move.windup + releases[releases.length - 1] + move.recovery;
    }
}
