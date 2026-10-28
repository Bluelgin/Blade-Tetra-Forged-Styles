package dev.bladetetra.easteregg;

/** Transient admission/payment clock. Rank itself remains in the native capability. */
final class SakuraFlowSession {
    static final int GRACE_TICKS = 14, CHARGE_INTERVAL = 10;
    boolean active;
    int startRank;
    long nextCharge;
    private long lastAttack = Long.MIN_VALUE, observedAction = Long.MIN_VALUE;
    private int observedStage;

    void observe(long now, int stage, long actionAt, int grade, long points) {
        if (active && (points <= 0 || now < lastAttack || now - lastAttack > GRACE_TICKS)) stop();
        boolean newAction = stage != observedStage || actionAt != observedAction;
        observedStage = stage;
        observedAction = actionAt;
        if (stage == 0) return;
        if (!active && newAction && actionAt <= now && now - actionAt <= 1 && grade >= 4 && points > 0) {
            active = true;
            startRank = Math.min(7, grade);
            nextCharge = now + CHARGE_INTERVAL;
        }
        if (active) lastAttack = now;
    }

    boolean valid(long now) {
        return active && now >= lastAttack && now - lastAttack <= GRACE_TICKS;
    }

    int charge(long now, long points, int perSecond, boolean enabled) {
        if (!valid(now) || !enabled || now < nextCharge) return 0;
        int cost = SakuraFlowRules.halfSecondCost(startRank, perSecond);
        nextCharge = now + CHARGE_INTERVAL;
        if (points < cost) { stop(); return 0; }
        if (points == cost) stop();
        return cost;
    }

    void stop() { active = false; }
}
