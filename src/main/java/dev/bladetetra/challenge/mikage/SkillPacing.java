package dev.bladetetra.challenge.mikage;

import java.util.HashMap;
import java.util.Map;

/** A committed signature requires two ordinary exchanges before another signature. */
public final class SkillPacing {
    private final Map<String, Long> readyAt = new HashMap<>();
    private int ordinarySinceSignature = 2;
    public boolean ready(String id, long tick) { return tick >= readyAt.getOrDefault(id, 0L); }
    public boolean signatureReady(String id, long tick) { return ordinarySinceSignature >= 2 && ready(id, tick); }
    public void committed(String id, long tick, int cooldown, boolean signature) {
        readyAt.put(id, tick + (signature ? cooldown : 60));
        ordinarySinceSignature = signature ? 0 : Math.min(2, ordinarySinceSignature + 1);
    }
}
