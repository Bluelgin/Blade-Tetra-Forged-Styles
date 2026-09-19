package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrails for the native-B Rengeki pursuit and kill-flow integration. */
class RengekiShortStepGuardTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/bladetetra/combat/RengekiShortStepHandler.java");

    @Test
    void pursuitHooksAuthoritativeComboMotionInsteadOfRawInputSync() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("BladeMotionEvent"),
                "Rengeki pursuit must hook Resharped's actual combo transition");
        assertFalse(source.contains("InputCommandEvent"),
                "Raw MoveInput synchronization does not carry transient L_CLICK/R_CLICK");
        assertFalse(source.contains("InputCommand.L_CLICK"),
                "L_CLICK is injected transiently by ItemSlashBlade, not MoveInputHandler");
        assertFalse(source.contains("InputCommand.R_CLICK"),
                "R_CLICK is injected transiently by ItemSlashBlade, not MoveInputHandler");
        assertTrue(source.contains("receiveCanceled = true"),
                "Canceled combo transitions must clear pending movement state");
    }

    @Test
    void pursuitOnlyRunsOnTheSixNativeBAdvances() throws IOException {
        String source = Files.readString(SOURCE);

        assertAdvance(source, "COMBO_B1", "COMBO_B2");
        assertAdvance(source, "COMBO_B2", "COMBO_B3");
        assertAdvance(source, "COMBO_B3", "COMBO_B4");
        assertAdvance(source, "COMBO_B4", "COMBO_B5");
        assertAdvance(source, "COMBO_B5", "COMBO_B6");
        assertAdvance(source, "COMBO_B6", "COMBO_B7");
    }

    @Test
    void nativeBTradingUsesSmallDamagePenaltyAcrossAuthoredTailSlashes() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("NATIVE_B_DAMAGE_MULTIPLIER = 0.92D"),
                "Rengeki native B damage tradeoff should remain the intended light 8% penalty");
        assertTrue(source.contains("public static void onRengekiSlash"),
                "Damage tradeoff should stay in the focused Rengeki handler");
        assertTrue(source.contains("!isNativeBFlowState(event.getSlashBladeState().getComboSeq())"),
                "Directional/aerial/Slash Art attacks must not inherit the native-B penalty");
        assertTrue(source.contains("event.setDamage(event.getDamage() * NATIVE_B_DAMAGE_MULTIPLIER)"),
                "Native B slash damage should be scaled exactly once at slash creation");
    }

    @Test
    void delayedHitsRemainBFlowOnlyWhileNativeRecoveryIsActive() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("static boolean isNativeBFlowState"),
                "Delayed native-B hits need an explicit flow-state predicate");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B1_END.getId().equals(combo)"),
                "B1 recovery must retain native-B provenance for delayed slash hits");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B1_END2.getId().equals(combo)"),
                "B1 end2 must retain delayed-hit provenance");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B1_END3.getId().equals(combo)"),
                "B1 end3 must retain delayed-hit provenance");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B_END.getId().equals(combo)"),
                "B2-B6 shared recovery must retain delayed-hit provenance");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B_END2.getId().equals(combo)"),
                "B2-B6 end2 must retain delayed-hit provenance");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B_END3.getId().equals(combo)"),
                "B2-B6 end3 must retain delayed-hit provenance");
        assertTrue(source.contains("ComboStateRegistry.COMBO_B7_END3.getId().equals(combo)"),
                "B7 recovery must retain delayed-hit provenance");
        assertFalse(source.contains("LivingDeathEvent"),
                "Resharped slash effects resolve damage through the shooter, so direct-entity death attribution is invalid here");
        assertFalse(source.contains("RENGEKI_B_SLASH_BLADES"),
                "Do not retain slash-entity provenance maps for a damage path that reports the shooter as attacker");
    }

    @Test
    void killsScheduleOneDeferredHandoffIncludingB7AndLateTailHits() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("private static final Map<UUID, KillTransfer> KILL_TRANSFERS"),
                "Confirmed kills need one dedicated pending hand-off slot per player");
        assertTrue(source.contains("KILL_TRANSFER_DELAY_TICKS = 1"),
                "Kill movement must be deferred out of the current slash hit iteration");
        assertTrue(source.contains("!event.getTarget().isAlive() || event.getTarget().getHealth() <= 0.0F"),
                "Kill hand-off must arm only after HitEvent confirms the target is dead");
        assertTrue(source.contains("if (!isNativeBFlowState(combo))"),
                "Active B nodes and their native recovery states should both recognize delayed lethal hits");
        assertTrue(source.contains("scheduleKillTransfer(player, event.getBlade())"),
                "A proven Rengeki B kill should schedule exactly one hand-off request");
        assertTrue(source.contains("public static void onPlayerTick(TickEvent.PlayerTickEvent event)"),
                "A kill must still auto-transfer when the player does not immediately press the next B beat");
    }

    @Test
    void killHandoffIsStrongerButStillBounded() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("KILL_TRANSFER_SEARCH_DISTANCE = 6.5D"),
                "Kill hand-off needs a slightly broader target search than ordinary chase");
        assertTrue(source.contains("MAX_KILL_TRANSFER_DISTANCE = 4.5D"),
                "Kill hand-off movement must remain bounded instead of becoming a long-range teleport");
        assertTrue(source.contains("Math.cos(Math.toRadians(80.0D))"),
                "Kill hand-off may use a wider forward cone but must not become 360-degree auto targeting");
    }

    @Test
    void movementIsBoundToTheSameBladeAndClearedOnPlayerReplacement() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("window.blade() != blade"),
                "An ordinary chase earned by one blade must not transfer to another Rengeki blade");
        assertTrue(source.contains("transfer.blade() != blade"),
                "A kill hand-off earned by one blade must not transfer to another Rengeki blade");
        assertTrue(source.contains("PlayerEvent.Clone"),
                "Death/respawn player replacement must clear transient Rengeki movement state");
        assertTrue(source.contains("KILL_TRANSFERS.remove(playerId)"),
                "Kill hand-off state must have explicit cleanup paths");
    }

    @Test
    void pursuitKeepsNativeTargetAndGroundSafety() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("TargetSelector.SlashBladeTargetingConditions"),
                "Pursuit must preserve Resharped's revenge-target and combat eligibility rules");
        assertTrue(source.contains("TargetSelector.AttackablePredicate"),
                "Pursuit targets must honor Resharped PVP/friendly targeting rules");
        assertTrue(source.contains("return player.onGround() && !player.isPassenger()"),
                "Neither ordinary chase nor kill hand-off may snap airborne/riding players to ground");
        assertTrue(source.contains("isCollisionAreaLoaded(level, sampleBox)"),
                "Path safety must validate the whole player collision footprint at chunk edges");
    }

    private static void assertAdvance(String source, String from, String to) {
        Pattern transition = Pattern.compile(
                "ComboStateRegistry\\." + from
                        + "\\.getId\\(\\)\\.equals\\(current\\)\\s*"
                        + "&&\\s*ComboStateRegistry\\." + to
                        + "\\.getId\\(\\)\\.equals\\(next\\)");
        assertTrue(transition.matcher(source).find(),
                () -> "Missing native Rengeki transition " + from + " -> " + to);
    }
}
