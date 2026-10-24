package dev.bladetetra.aura;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Shared per-victim aura cadence, independent of melee and other damage sources. Server thread only. */
final class SwordAuraHitCadence<T> {
    static final int INTERVAL_TICKS = 4;
    private final Map<T, Long> lastHit = new WeakHashMap<>();

    boolean tryHit(T target, long tick, BooleanSupplier damage) {
        Long previous = lastHit.get(target);
        if (previous != null && (tick < previous || tick - previous < INTERVAL_TICKS)) return false;
        // Reserve before hurt: nested Forge callbacks cannot recursively spend the same hit slot.
        lastHit.put(target, tick);
        boolean connected = false;
        try {
            connected = damage.getAsBoolean();
            return connected;
        } finally {
            if (!connected) {
                if (previous == null) lastHit.remove(target);
                else lastHit.put(target, previous);
            }
        }
    }
}
