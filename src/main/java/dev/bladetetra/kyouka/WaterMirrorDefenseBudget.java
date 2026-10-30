package dev.bladetetra.kyouka;

import java.util.ArrayDeque;

/** Only short-lived reductions, retained independently of the form and weapon. */
final class WaterMirrorDefenseBudget {
    private record Reduction(long tick, float amount) {}
    private final ArrayDeque<Reduction> reductions = new ArrayDeque<>();
    private long next = Long.MIN_VALUE;
    float available(long now, float damage, int air, int maximum) {
        while (!reductions.isEmpty() && now - reductions.peekFirst().tick >= 200) reductions.removeFirst();
        if (now < next || maximum <= 0 || damage <= 0 || !Float.isFinite(damage)) return 0;
        float used = 0;
        for (var r : reductions) used += r.amount;
        return Math.max(0, Math.min(6 - used, damage * .30F * Math.max(0, Math.min(1, air / (float)maximum))));
    }
    void commit(long now, float amount) {
        if (amount <= 0) return;
        reductions.addLast(new Reduction(now, amount)); next = now + 60;
    }
    boolean expired(long now) { return now >= next && (reductions.isEmpty() || now - reductions.peekLast().tick >= 200); }
}
