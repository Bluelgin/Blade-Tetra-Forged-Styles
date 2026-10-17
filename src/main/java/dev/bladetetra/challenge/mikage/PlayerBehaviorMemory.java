package dev.bladetetra.challenge.mikage;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static dev.bladetetra.challenge.mikage.CombatObservation.Behavior.*;

/** Bounded rolling evidence, isolated per participant and per encounter. */
public final class PlayerBehaviorMemory {
    private static final int WINDOW = 60;
    private static final int MIN_SAMPLES = 8;
    private final Map<UUID, History> histories = new HashMap<>();

    public record Sample(long tick, double distance, double height,
            double radialMovement, boolean guarding, boolean airborne) {}
    private record Art(long tick, String id) {}
    private static final class History {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        final ArrayDeque<Long> attacks = new ArrayDeque<>();
        final ArrayDeque<Art> arts = new ArrayDeque<>();
        long seen;
    }

    public void sample(UUID participant, Sample sample) {
        History history = histories.computeIfAbsent(participant, id -> new History());
        if (!history.samples.isEmpty() && history.samples.peekLast().tick() >= sample.tick()) return;
        history.seen = sample.tick();
        history.samples.addLast(sample);
        trim(history, sample.tick());
    }

    public void attack(UUID participant, long tick) {
        History history = histories.get(participant);
        if (history == null) return;
        // Multi-hit/projectile attacks in one tick are one observation.
        if (history.attacks.isEmpty() || tick - history.attacks.peekLast() >= 4) {
            history.attacks.addLast(tick);
        }
        trim(history, tick);
    }

    public void slashArt(UUID participant, long tick, String id) {
        History history = histories.get(participant);
        if (history == null || id == null || id.isBlank()) return;
        if (history.arts.isEmpty() || history.arts.peekLast().tick() != tick) {
            history.arts.addLast(new Art(tick, id));
        }
        trim(history, tick);
    }

    public CombatObservation observation(UUID participant, long tick) {
        History history = histories.get(participant);
        if (history == null || tick - history.seen > 2) return null;
        trim(history, tick);
        Sample current = history.samples.peekLast();
        if (current == null) return null;
        Map<CombatObservation.Behavior, Double> evidence = new EnumMap<>(CombatObservation.Behavior.class);
        evidence.put(APPROACHING, fraction(history, sample -> sample.radialMovement() < -0.035));
        evidence.put(RETREATING, fraction(history, sample -> sample.radialMovement() > 0.035));
        evidence.put(GUARDING, fraction(history, Sample::guarding));
        evidence.put(AIRBORNE, fraction(history, Sample::airborne));
        evidence.put(PRESSURE, Math.min(1.0, history.attacks.size() / 5.0));
        int repeats = 0;
        if (!history.arts.isEmpty()) {
            String latest = history.arts.peekLast().id();
            repeats = (int) history.arts.stream().filter(art -> art.id().equals(latest)).count();
        }
        evidence.put(REPEATED_ART, Math.min(1.0, Math.max(0, repeats - 1) / 3.0));
        return new CombatObservation(participant, tick, current.distance(), current.height(), evidence);
    }

    private static double fraction(History history, Predicate<Sample> predicate) {
        // Recent movement outweighs a habit the player has already stopped.
        long latest = history.samples.peekLast().tick();
        var recent = history.samples.stream().filter(sample -> latest - sample.tick() < 20).toList();
        return recent.stream().filter(predicate).count() / (double) Math.max(MIN_SAMPLES, recent.size());
    }

    private static void trim(History history, long tick) {
        while (!history.samples.isEmpty() && tick - history.samples.peekFirst().tick() >= WINDOW) history.samples.removeFirst();
        while (history.samples.size() > WINDOW) history.samples.removeFirst();
        while (!history.attacks.isEmpty() && tick - history.attacks.peekFirst() >= WINDOW) history.attacks.removeFirst();
        while (!history.arts.isEmpty() && tick - history.arts.peekFirst().tick() >= WINDOW) history.arts.removeFirst();
        while (history.arts.size() > 32) history.arts.removeFirst();
    }

    public void retain(java.util.Set<UUID> participants, long tick) {
        histories.entrySet().removeIf(entry -> !participants.contains(entry.getKey()) || tick - entry.getValue().seen >= WINDOW);
    }

    public void clear() { histories.clear(); }
}
