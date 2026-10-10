package dev.bladetetra.challenge.mikage;

/** Per-cast duel rhythm. An evasion resets the streak, never finishes the pursuit. */
public final class ThousandGatesSequence {
    public enum Stage { ENTER, HIDDEN, CUE, REVEAL, RECOVER, BROKEN, HIT }
    public enum Contact { PARRY, MISS, HIT }
    public static final int REQUIRED_PARRIES = 5;
    private Stage stage = Stage.ENTER;
    private int remaining = 12, streak;

    public Stage stage() { return stage; }
    public int streak() { return streak; }
    public int remaining() { return remaining; }
    public int cueTicks() { return 16 - streak * 2; }
    public int revealTicks() { return 8 - streak; }
    public boolean terminal() { return stage == Stage.BROKEN || stage == Stage.HIT; }

    /** Returns true at a phase boundary; REVEAL waits for one authoritative contact result. */
    public boolean tick() {
        if (terminal() || remaining == 0 || --remaining > 0) return false;
        if (stage == Stage.REVEAL) return true;
        stage = switch (stage) {
            case ENTER, RECOVER -> Stage.HIDDEN;
            case HIDDEN -> Stage.CUE;
            case CUE -> Stage.REVEAL;
            default -> stage;
        };
        remaining = switch (stage) {
            case HIDDEN -> 10;
            case CUE -> cueTicks();
            case REVEAL -> revealTicks();
            default -> 0;
        };
        return true;
    }

    /** If no safe arrival exists, remain hidden and retry without an invisible attack. */
    public void retryArrival() {
        if (stage != Stage.CUE && (stage != Stage.REVEAL || remaining == 0))
            throw new IllegalStateException("Arrival outside cue");
        stage = Stage.HIDDEN;
        remaining = 10;
    }

    public void contact(Contact contact) {
        if (stage != Stage.REVEAL || remaining != 0)
            throw new IllegalStateException("Contact outside strike");
        if (contact == Contact.HIT) { stage = Stage.HIT; return; }
        streak = contact == Contact.PARRY ? streak + 1 : 0;
        if (streak == REQUIRED_PARRIES) { stage = Stage.BROKEN; return; }
        stage = Stage.RECOVER;
        remaining = 6;
    }
}
