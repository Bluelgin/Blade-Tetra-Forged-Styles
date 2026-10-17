package dev.bladetetra.challenge.mikage;

import java.util.*;

/** Continuous pressure ends by breaking the core, never by waiting out a wave counter. */
public final class GateBarrageSequence<T> {
    public enum Stage { OPEN, CHARGE, FIRING, BREAK, COMPLETE }
    public static final int OPEN_TICKS = 10, CHARGE_TICKS = 30, BREAK_TICKS = 16;
    public static final int CORE_HITS = 3, CORE_INTERVAL = 12, INPUT_LIFETIME = 20, DAMAGE_INTERVAL = 10;
    public static final int VOLLEY_INTERVAL = 4, MAX_SWORDS = 64, SWORD_LIFETIME = 80;
    private final Map<UUID, Swing<T>> swings = new HashMap<>();
    private final Map<UUID, Long> playerHits = new HashMap<>();
    private Stage stage = Stage.OPEN;
    private int remaining = OPEN_TICKS, cracks, firingTicks;
    private long lastCoreHit = Long.MIN_VALUE, pauseUntil = Long.MIN_VALUE;
    public Stage stage() { return stage; }
    public int remainingHits() { return CORE_HITS - cracks; }
    public void tick() {
        if (stage == Stage.FIRING) { firingTicks++; return; }
        if (stage == Stage.COMPLETE || --remaining > 0) return;
        switch (stage) {
            case OPEN -> { stage = Stage.CHARGE; remaining = CHARGE_TICKS; }
            case CHARGE -> stage = Stage.FIRING;
            case BREAK -> stage = Stage.COMPLETE;
            default -> { }
        }
    }
    public boolean volley(long now, int liveSwords) {
        return stage == Stage.FIRING && now >= pauseUntil && firingTicks % VOLLEY_INTERVAL == 0
                && liveSwords <= MAX_SWORDS - 5;
    }
    public void swing(UUID player, long now, T weapon) {
        if (weapon == null) return;
        var old = swings.get(player);
        if (old == null || old.tick != now) swings.put(player, new Swing<>(weapon, now));
    }
    public boolean coreHit(UUID player, long now, T weapon) {
        var swing = swings.get(player);
        if (stage != Stage.FIRING || swing == null || swing.used || swing.weapon != weapon
                || now < swing.tick || now - swing.tick > INPUT_LIFETIME
                || lastCoreHit != Long.MIN_VALUE && now - lastCoreHit < CORE_INTERVAL) return false;
        swing.used = true; lastCoreHit = now; pauseUntil = now + CORE_INTERVAL;
        if (++cracks == CORE_HITS) { stage = Stage.BREAK; remaining = BREAK_TICKS; }
        return true;
    }
    public boolean mayDamage(UUID player, long now) {
        long last = playerHits.getOrDefault(player, Long.MIN_VALUE);
        return stage == Stage.FIRING && (last == Long.MIN_VALUE || now - last >= DAMAGE_INTERVAL);
    }
    public void damaged(UUID player, long now) { playerHits.put(player, now); }
    public void retain(Set<UUID> players, long now) {
        swings.entrySet().removeIf(e -> !players.contains(e.getKey()) || now - e.getValue().tick > INPUT_LIFETIME);
        playerHits.entrySet().removeIf(e -> !players.contains(e.getKey()) || now - e.getValue() >= DAMAGE_INTERVAL);
    }
    private static final class Swing<T> {
        final T weapon; final long tick; boolean used;
        Swing(T weapon, long tick) { this.weapon = weapon; this.tick = tick; }
    }
}
