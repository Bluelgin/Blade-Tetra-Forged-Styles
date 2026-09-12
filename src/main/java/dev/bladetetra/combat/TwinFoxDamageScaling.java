package dev.bladetetra.combat;

/** Pure damage scaling used by both Twin Fox abilities. */
final class TwinFoxDamageScaling {
    private static final double SOFT_THRESHOLD = 24.0D;
    private static final double POST_THRESHOLD_SCALE = 0.40D;
    private static final double EFFECTIVE_ATTACK_CAP = 64.0D;

    private static final double MOONHUNT_PER_FOX_SCALE = 0.40D;
    private static final double MOONHUNT_FINISHER_SCALE = 0.85D;
    private static final double PURSUIT_SCALE = 0.65D;
    private static final double MIN_DAMAGE = 0.5D;

    static double effectiveAttack(double attack) {
        double effective = attack <= SOFT_THRESHOLD
                ? attack
                : SOFT_THRESHOLD
                        + (attack - SOFT_THRESHOLD) * POST_THRESHOLD_SCALE;
        return Math.min(EFFECTIVE_ATTACK_CAP, effective);
    }

    static DamageSnapshot snapshot(double attack) {
        double effective = effectiveAttack(attack);
        return new DamageSnapshot(
                effective,
                Math.max(MIN_DAMAGE, effective * MOONHUNT_PER_FOX_SCALE),
                Math.max(MIN_DAMAGE, effective * MOONHUNT_FINISHER_SCALE),
                Math.max(MIN_DAMAGE, effective * PURSUIT_SCALE));
    }

    static double moonhuntTotal(DamageSnapshot snapshot, int foxHits) {
        int hits = Math.max(0, Math.min(2, foxHits));
        double damage = hits * snapshot.perFoxDamage();
        return hits == 2 ? damage + snapshot.finisherDamage() : damage;
    }

    record DamageSnapshot(double effectiveAttack, double perFoxDamage,
            double finisherDamage, double pursuitDamage) {
    }

    private TwinFoxDamageScaling() {
    }
}
