package dev.bladetetra.challenge.mikage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adapted from Contract-Blade's BlackFoxBalance/BlackFoxEncounters; see bundled MIT notice. */
public final class DuelDefenseState<T> {
    public static final int PARRY_WINDOW = 3, COUNTER_COOLDOWN = 60, GUARD_TICKS = 8;
    public static final int PROTECTION_TICKS = 24, REQUIRED_PARRIES = 5;
    public static final int STAGGER_TICKS = 100, HIT_RECOVERY = 10;
    private final Map<UUID, Swing<T>> swings = new HashMap<>();
    private final Map<UUID, Long> protection = new HashMap<>();
    private long counterAt = Long.MIN_VALUE, guardingUntil = Long.MIN_VALUE;
    private long staggerUntil = Long.MIN_VALUE, lastParry = Long.MIN_VALUE;
    private int progress;

    public void swing(UUID player, long now, T weapon) {
        if (weapon == null) return;
        Swing<T> previous = swings.get(player);
        // AttackEntityEvent and native motion can describe the same server-tick swing.
        if (previous != null && previous.tick == now) return;
        swings.put(player, new Swing<>(weapon, now));
    }

    public boolean consumeSwing(UUID player, long now, T weapon) {
        Swing<T> swing = swings.get(player);
        if (swing == null || swing.consumed || swing.weapon != weapon
                || now < swing.tick || now - swing.tick > PARRY_WINDOW) return false;
        swing.consumed = true;
        return true;
    }

    public boolean counterReady(long now) { return now >= counterAt && !staggered(now); }
    public void counterStarted(long now) {
        counterAt = now + COUNTER_COOLDOWN;
        guardingUntil = now + GUARD_TICKS;
    }
    public boolean guarding(long now) { return now < guardingUntil && !staggered(now); }
    public void cancelGuard() { guardingUntil = Long.MIN_VALUE; }
    public boolean protects(UUID player, long now) { return now < protection.getOrDefault(player, Long.MIN_VALUE); }

    /** One confirmed sword contact; simultaneous multi-hit contacts cannot fill the meter. */
    public boolean parried(UUID player, long now) {
        protect(player, now);
        if (staggered(now) || lastParry != Long.MIN_VALUE && now - lastParry < 4) return false;
        lastParry = now;
        if (++progress < REQUIRED_PARRIES) return false;
        breakBalance(now);
        return true;
    }

    public void protect(UUID player, long now) { protection.put(player, now + PROTECTION_TICKS); }
    public void breakBalance(long now) {
        progress = 0;
        lastParry = now;
        staggerUntil = now + STAGGER_TICKS;
        cancelGuard();
    }

    /** A knockdown retains earned progress and uses the same recovery clock as full balance break. */
    public void openStagger(long now, int duration) { staggerUntil = now + duration; cancelGuard(); }
    public boolean staggered(long now) { return now < staggerUntil; }
    public int staggerRemaining(long now) { return (int) Math.max(0, staggerUntil - now); }
    public int progress(long now) { return staggered(now) ? REQUIRED_PARRIES : progress; }
    public void hitAccepted(long now) {
        if (staggered(now)) staggerUntil = Math.max(now + 1, staggerUntil - HIT_RECOVERY);
    }

    public void retain(Set<UUID> participants, long now) {
        swings.entrySet().removeIf(entry -> !participants.contains(entry.getKey())
                || now - entry.getValue().tick > PARRY_WINDOW);
        protection.entrySet().removeIf(entry -> !participants.contains(entry.getKey()) || now >= entry.getValue());
    }

    public void clear() {
        swings.clear(); protection.clear(); progress = 0;
        counterAt = guardingUntil = staggerUntil = lastParry = Long.MIN_VALUE;
    }

    private static final class Swing<T> {
        final T weapon;
        final long tick;
        boolean consumed;
        Swing(T weapon, long tick) { this.weapon = weapon; this.tick = tick; }
    }
}
