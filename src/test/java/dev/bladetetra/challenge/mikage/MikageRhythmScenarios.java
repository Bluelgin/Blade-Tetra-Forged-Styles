package dev.bladetetra.challenge.mikage;

import java.util.*;

/** Executable phrases under fresh input, repeated contact, escape and interruption. */
public final class MikageRhythmScenarios {
    private static int assertions;
    public static void main(String[] args) {
        runAll(); System.out.println("Mikage rhythm scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        threeClashesHaveStableBeatsAndOnlyTheFinisherOpens();
        oneInputCannotParryTheNextBladeInsideProtection();
        nativeHitEndsThePhraseWithoutTheRemainingBlades();
        escapeHasTwoPursuitsAndALockedFinisher();
        everyMeleePhraseEndsAndHeavyHasAnAudibleDelay();
    }
    private static void threeClashesHaveStableBeatsAndOnlyTheFinisherOpens() {
        var rhythm = new MeleeRhythm(MikageMove.COMBO);
        List<Integer> actual = new ArrayList<>();
        for (int frame = 1; frame <= 100 && !rhythm.complete(); frame++) {
            rhythm.tick();
            if (!rhythm.releaseDue()) continue;
            int beat = rhythm.release(); actual.add(frame);
            check(rhythm.parry(beat), "fresh contact accepted");
            check(!rhythm.mayContact(beat), "same beat's secondary native entities cannot hit after parry");
            check(!rhythm.parry(beat), "duplicate contact cannot extend the pause or fill balance");
            check(rhythm.countered() == (beat == 2), "only the finisher ends the phrase");
            if (beat > 0) check(!rhythm.mayContact(beat - 1), "previous blade cannot touch during a later beat");
        }
        check(actual.equals(List.of(10, 26, 40)), "authored 14/12 rhythm plus two-tick clashes, no random timings");
        check(rhythm.complete() && rhythm.countered(), "three successful contacts end in an opening");
    }
    private static void oneInputCannotParryTheNextBladeInsideProtection() {
        var defense = new DuelDefenseState<Object>(); Object blade = new Object(); UUID player = new UUID(0, 1);
        var rhythm = new MeleeRhythm(MikageMove.IAIDO);
        while (!rhythm.releaseDue()) rhythm.tick();
        int first = rhythm.release(); defense.swing(player, 100, blade);
        check(defense.consumeSwing(player, 100, blade), "first blade consumes real input");
        defense.parried(player, 100); rhythm.parry(first);
        while (!rhythm.releaseDue()) rhythm.tick();
        int second = rhythm.release();
        check(defense.protects(player, 116), "old contact protection still exists");
        check(!defense.consumeSwing(player, 116, blade), "old swing cannot be used again");
        defense.swing(player, 116, blade);
        check(defense.consumeSwing(player, 116, blade), "new input works during previous protection");
        check(rhythm.parry(second) && rhythm.countered(), "second fresh clash beats the finisher");
    }
    private static void nativeHitEndsThePhraseWithoutTheRemainingBlades() {
        var rhythm = new MeleeRhythm(MikageMove.CLEAVE);
        while (!rhythm.releaseDue()) rhythm.tick();
        int beat = rhythm.release(); rhythm.hit(beat);
        check(rhythm.hit() && !rhythm.mayContact(beat), "accepted hit shuts off contact immediately");
        for (int i = 0; i < MeleeRhythm.HIT_RECOVERY - 1; i++) {
            rhythm.tick(); check(!rhythm.releaseDue(), "no later blade during hit recovery");
        }
        check(!rhythm.complete(), "brief hit recovery remains"); rhythm.tick();
        check(rhythm.complete() && rhythm.beat() == 0, "only one blade released before hit ending");
    }
    private static void escapeHasTwoPursuitsAndALockedFinisher() {
        var rhythm = new MeleeRhythm(MikageMove.COMBO);
        while (!rhythm.releaseDue()) rhythm.tick(); rhythm.release();
        while (rhythm.untilNextBeat() > 6) rhythm.tick();
        check(!rhythm.beginPursuit(false, 8), "standing far away alone does not manufacture an escape");
        check(!rhythm.beginPursuit(true, 3), "no pursuit inside melee distance");
        check(rhythm.beginPursuit(true, 8), "first retreat earns a visible pursuit step");
        check(!rhythm.beginPursuit(true, 8), "the same tell cannot renew a pursuit attempt");
        while (!rhythm.releaseDue()) rhythm.tick(); rhythm.release();
        while (rhythm.untilNextBeat() > 6) rhythm.tick();
        check(rhythm.beginPursuit(true, 8), "second retreat earns last pursuit");
        rhythm.tick(); rhythm.tick();
        check(!rhythm.beginPursuit(true, 12), "locked finisher cannot request a new homing pursuit");
        while (!rhythm.releaseDue()) rhythm.tick(); rhythm.release();
        check(!rhythm.beginPursuit(true, 20), "no fourth blade or renewed pursuit after finisher");
        check(rhythm.pursuits() == 2, "two attempts shared by the whole phrase");
    }
    private static void everyMeleePhraseEndsAndHeavyHasAnAudibleDelay() {
        for (MikageMove move : MikageMove.values()) if (move.melee()) {
            var rhythm = new MeleeRhythm(move); int releases = 0;
            for (int frame = 0; frame < 200 && !rhythm.complete(); frame++) {
                rhythm.tick(); if (rhythm.releaseDue()) { rhythm.release(); releases++; }
            }
            check(rhythm.complete(), move + " has finite whiff recovery");
            check(releases == move.releases().length, move + " keeps its authored beat count");
        }
        int[] heavy = MikageMove.CLEAVE.releases();
        check(heavy[2] - heavy[1] > heavy[1] - heavy[0], "heavy finisher delays instead of flattening every beat");
        check(MikageMove.COMBO.windup == 10 && MikageMove.CLEAVE.windup == 22, "higher frequency preserves opening tells");
    }
    private static void check(boolean value, String detail) {
        assertions++; if (!value) throw new AssertionError(detail);
    }
    private MikageRhythmScenarios() { }
}
