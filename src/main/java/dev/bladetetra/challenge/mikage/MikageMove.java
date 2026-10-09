package dev.bladetetra.challenge.mikage;

import java.util.Set;
import static dev.bladetetra.challenge.mikage.SkillSpec.Tactic.*;

/** Authored ordinary attacks. Timing gates release native attacks, never deferred damage. */
public enum MikageMove {
    COMBO(1, 10, 28, 8, CLOSE), IAIDO(1, 16, 20, 10, GAP_CLOSE),
    CIRCLE(1, 14, 22, 6, COUNTER), CLEAVE(2, 22, 30, 8, GUARD_PRESSURE),
    SAKURA(2, 18, 28, 8, CLOSE), DUEL(1, 16, 24, 10, GAP_CLOSE),
    ZANSHIN(2, 18, 24, 8, COUNTER), WAVE(1, 18, 22, 40, RANGED),
    FAN(1, 22, 26, 40, RANGED), CUT(2, 24, 28, 36, SETUP),
    SUPER_CUT(3, 30, 32, 40, SETUP), VOLLEY(1, 22, 26, 40, ANTI_AIR),
    RAIN(2, 26, 30, 40, ANTI_AIR), CHASE_RAIN(2, 24, 32, 40, COUNTER);
    public final int phase, windup, recovery, range;
    private final SkillSpec.Tactic tactic;
    MikageMove(int phase, int windup, int recovery, int range, SkillSpec.Tactic tactic) {
        this.phase = phase; this.windup = windup; this.recovery = recovery; this.range = range; this.tactic = tactic;
    }
    public boolean melee() { return ordinal() <= ZANSHIN.ordinal(); }
    public String id() { return "blade_tetra:swordplay/" + name().toLowerCase(java.util.Locale.ROOT); }
    public SkillSpec spec() { return new SkillSpec(id(), melee() ? "blade" : "ranged", Set.of(tactic), 0, range); }
    public int[] releases() {
        return switch (this) {
            case DUEL -> new int[]{0, 6};
            case COMBO -> new int[]{0, 10, 22};
            case SAKURA -> new int[]{0, 12, 28};
            case SUPER_CUT -> new int[]{0, 12, 24};
            case VOLLEY -> new int[]{0, 9};
            case RAIN -> new int[]{0, 12, 24};
            case CHASE_RAIN -> new int[]{0, 14, 28, 44};
            default -> new int[]{0};
        };
    }
}
