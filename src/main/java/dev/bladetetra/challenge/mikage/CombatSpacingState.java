package dev.bladetetra.challenge.mikage;

import java.util.UUID;

/** Ordinary footwork, not a skill. Retreat has a finite budget until a melee exchange completes. */
public final class CombatSpacingState {
    public enum Motion { HOLD, ORBIT, RETREAT, CLOSE, ENGAGE }
    public static final double RETREAT_START = 5.5, RETREAT_END = 6.5;
    public static final double CLOSE_START = 8.5, CLOSE_END = 7.2, EXCHANGE_RANGE = 5.2;
    public static final int MAX_RETREATS = 2, STEP_TICKS = 12, BREATH_TICKS = 8;
    private UUID target;
    private Motion motion = Motion.HOLD;
    private int retreats;
    private long stepUntil, decideAt;
    public Motion motion() { return motion; }
    public int retreats() { return retreats; }
    public boolean exchangeRequired(UUID player) {
        return player != null && player.equals(target) && retreats >= MAX_RETREATS && motion != Motion.RETREAT;
    }
    public Motion update(UUID player, double distance, long now) {
        if (player == null || !Double.isFinite(distance) || distance < 0) { clear(); return motion; }
        if (!player.equals(target)) { clear(); target = player; }
        if (motion == Motion.RETREAT) {
            if (now < stepUntil && distance < RETREAT_END) return motion;
            motion = Motion.HOLD; decideAt = now + BREATH_TICKS;
        }
        if (exchangeRequired(player)) {
            motion = distance <= EXCHANGE_RANGE ? Motion.ENGAGE : Motion.CLOSE;
            return motion;
        }
        if (motion == Motion.CLOSE && distance > CLOSE_END) return motion;
        if (now < decideAt && motion != Motion.CLOSE) return motion;
        decideAt = now + 6;
        if (distance < RETREAT_START) {
            retreats++; motion = Motion.RETREAT; stepUntil = now + STEP_TICKS;
        } else motion = distance > CLOSE_START ? Motion.CLOSE : Motion.ORBIT;
        return motion;
    }
    /** Pauses preserve the retreat budget; a spell or recovery cannot grant endless fresh dodges. */
    public void pause(long now) { motion = Motion.HOLD; decideAt = now + BREATH_TICKS; }
    public void blocked(long now) { pause(now); }
    public void exchangeCompleted(UUID player, long now) {
        if (player != null && player.equals(target)) { retreats = 0; pause(now); }
    }
    public void clear() { target = null; retreats = 0; motion = Motion.HOLD; stepUntil = decideAt = 0; }
}
