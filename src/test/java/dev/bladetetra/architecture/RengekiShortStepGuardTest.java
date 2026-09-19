package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrails for the native-B Rengeki pursuit, kill-flow and momentum layers. */
class RengekiShortStepGuardTest {
    private static final Path MOVEMENT_SOURCE = Path.of(
            "src/main/java/dev/bladetetra/combat/RengekiShortStepHandler.java");
    private static final Path MOMENTUM_SOURCE = Path.of(
            "src/main/java/dev/bladetetra/combat/RengekiMomentumHandler.java");

    @Test
    void pursuitHooksAuthoritativeComboMotionInsteadOfRawInputSync() throws IOException {
        String source = Files.readString(MOVEMENT_SOURCE);

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
        String source = Files.readString(MOVEMENT_SOURCE);

        assertAdvance(source, "COMBO_B1", "COMBO_B2");
        assertAdvance(source, "COMBO_B2", "COMBO_B3");
        assertAdvance(source, "COMBO_B3", "COMBO_B4");
        assertAdvance(source, "COMBO_B4", "COMBO_B5");
        assertAdvance(source, "COMBO_B5", "COMBO_B6");
        assertAdvance(source, "COMBO_B6", "COMBO_B7");
    }

    @Test
    void nativeBTradingUsesStrongerPenaltyAcrossAuthoredTailSlashes() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("NATIVE_B_DAMAGE_MULTIPLIER = 0.88D"),
                "Rengeki native B damage should trade 12% raw damage for its expanded flow tools");
        assertTrue(source.contains("public static void onRengekiSlash"),
                "Damage tradeoff should stay in the focused Rengeki momentum handler");
        assertTrue(source.contains("RengekiShortStepHandler.isNativeBFlowState"),
                "Directional/aerial/Slash Art attacks must not inherit the native-B penalty");
        assertTrue(source.contains("event.setDamage(event.getDamage() * NATIVE_B_DAMAGE_MULTIPLIER)"),
                "Native B slash damage should be scaled exactly once at slash creation");
    }

    @Test
    void delayedHitsRemainBFlowOnlyWhileNativeRecoveryIsActive() throws IOException {
        String source = Files.readString(MOVEMENT_SOURCE);

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
        String source = Files.readString(MOVEMENT_SOURCE);

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
        String source = Files.readString(MOVEMENT_SOURCE);

        assertTrue(source.contains("KILL_TRANSFER_SEARCH_DISTANCE = 6.5D"),
                "Kill hand-off needs a slightly broader target search than ordinary chase");
        assertTrue(source.contains("MAX_KILL_TRANSFER_DISTANCE = 4.5D"),
                "Kill hand-off movement must remain bounded instead of becoming a long-range teleport");
        assertTrue(source.contains("Math.cos(Math.toRadians(80.0D))"),
                "Kill hand-off may use a wider forward cone but must not become 360-degree auto targeting");
    }

    @Test
    void sprintFlowIsIdleGroundedAndRateLimited() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_SLASH_INTERVAL_TICKS = 10"),
                "Sprint B beats must stay rate-limited instead of dealing a real hit every tick");
        assertTrue(source.contains("ComboStateRegistry.NONE.getId().equals(combo)"),
                "Sprint flow must be idle-only and never stack on top of active combo damage");
        assertTrue(source.contains("!player.isSprinting()"),
                "Sprint flow must require an actual sprint state");
        assertTrue(source.contains("!player.onGround()"),
                "Sprint flow must not become a free aerial attack");
        assertTrue(source.contains("RengekiShortStepHandler.hasPendingKillTransfer(playerId)"),
                "Sprint flow must not race a pending kill hand-off");
    }

    @Test
    void sprintSpeedUsesRealTickDisplacementInsteadOfFrictionDampedMotion() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("private static final Map<UUID, MovementSample> MOVEMENT_SAMPLES"),
                "Normal sprint detection needs one previous server position sample per player");
        assertTrue(source.contains("sampleHorizontalDisplacement(playerId, player.position(), now)"),
                "Sprint scaling must use actual movement between server ticks");
        assertTrue(source.contains("new MovementSample(position, gameTime)"),
                "Every held-Rengeki tick should refresh the movement sample");
        assertTrue(source.contains("gameTime - previous.gameTime() != 1L"),
                "Non-consecutive samples must not fabricate speed after login/dimension changes");
        assertFalse(source.contains("private static double horizontalSpeed(ServerPlayer player)"),
                "Do not regress to END-tick deltaMovement, which made V rushes trigger while normal sprinting failed");
    }

    @Test
    void sprintFlowCyclesThroughAVisualB1ToB7Rhythm() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_B_CHAIN_LENGTH = 7"),
                "Sprint flow should explicitly model B1 through B7 visual beats");
        assertTrue(source.contains("SPRINT_B_BURST_TICKS = 7"),
                "B2-B6 style rush visuals should retain the native seven-tick cadence");
        assertTrue(source.contains("chain.nextBeat = (chain.nextBeat + 1) % SPRINT_B_CHAIN_LENGTH"),
                "Continuous sprinting must cycle B1 -> ... -> B7 -> B1");
        assertTrue(source.contains("emitSprintBVisualTick"),
                "Sprint flow should emit a timed B-style burst rather than one generic slash");
        assertTrue(source.contains("visualSize, -30.0F"),
                "B1-inspired sprint beat should open with the native-like crossed slash angle");
        assertTrue(source.contains("visualSize, 145.0F"),
                "B1-inspired sprint beat should include the mirrored crossed slash");
        assertTrue(source.contains("mirrored ? 90.0F : -90.0F"),
                "B2-B6 visuals should alternate the same positive/negative roll families as native B");
        assertTrue(source.contains("beat == SPRINT_B_CHAIN_LENGTH - 1"),
                "B7-inspired sprint beat should have an explicit visual finisher");
    }

    @Test
    void sprintRangeAndDamageScaleWithSpeedButHaveHardSpeedCaps() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_SLASH_MIN_SPEED = 0.12D"),
                "Tiny movement noise must not trigger the sprint flow");
        assertTrue(source.contains("SPRINT_SLASH_SPEED_CAP = 0.36D"),
                "Movement mods or extreme speed effects need a hard scaling cap");
        assertTrue(source.contains("SPRINT_SLASH_MIN_RANGE = 1.35D"),
                "Normal sprint should begin with a deliberately small frontal range");
        assertTrue(source.contains("SPRINT_SLASH_MAX_RANGE = 2.75D"),
                "Sprint range must remain bounded even at extreme speed");
        assertTrue(source.contains("sprintSlashRangeForSpeed"),
                "Speed-to-range scaling should stay explicit and testable");
        assertTrue(source.contains("SPRINT_SLASH_MIN_VISUAL_SIZE = 0.28F"),
                "The visual should start substantially smaller than a normal B slash");
        assertTrue(source.contains("SPRINT_SLASH_MAX_VISUAL_SIZE = 0.58F"),
                "Visual scaling must have its own hard ceiling");
        assertTrue(source.contains("SPRINT_SLASH_MIN_DAMAGE_RATIO = 0.06F"),
                "Low-speed sprint hit should inherit panel scaling at a conservative 0.06 ratio");
        assertTrue(source.contains("SPRINT_SLASH_MAX_DAMAGE_RATIO = 0.14F"),
                "Only the speed-provided damage ratio should cap, at 0.14");
        assertTrue(source.contains("sprintSlashDamageRatioForSpeed"),
                "Speed-to-damage scaling should be explicit and share the same capped speed factor");
    }

    @Test
    void sprintVisualFlurryStillDealsOnlyOnePanelScaledHitPerBeat() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("private static LivingEntity selectSprintSlashTarget"),
                "Sprint flow should select one frontal target instead of becoming passive AoE farming");
        assertTrue(source.contains("applySprintSlashHit(player, target, damageRatio)"),
                "Each sprint B beat should resolve exactly one low-ratio real hit after starting its visual burst");
        assertTrue(source.contains("AttackManager.doMeleeAttack("),
                "The real hit should reuse Resharped's panel-scaled melee compatibility path");
        assertTrue(source.contains("false,\n                    false,\n                    damageRatio"),
                "Sprint hit must respect normal hurt invulnerability and apply the capped speed ratio to panel damage");
        assertFalse(source.contains("AttackManager.doSlash(player"),
                "B-style visual cadence must not use native owned slash effects that would secretly multiply damage");
    }

    @Test
    void sprintHitPreservesMomentumAndSprintFlag() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("player.setSprinting(false)"),
                "Sprint-hit knockback must be suppressed during the compatibility attack call");
        assertTrue(source.contains("player.setDeltaMovement(momentum)"),
                "Player momentum must be restored after the passive hit");
        assertTrue(source.contains("player.setSprinting(sprinting)"),
                "The exact pre-hit sprint flag must be restored so the passive cannot cancel sprinting");
    }

    @Test
    void sprintVisualCannotSecretlyApplyNativeAreaDamage() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("new EntitySlashEffect("),
                "Sprint B visuals should reuse the native slash-effect renderer");
        assertFalse(source.contains("effect.setOwner(player)"),
                "Visual-only slashes must not gain a shooter and run EntitySlashEffect's broad native areaAttack");
        assertTrue(source.contains("effect.setBaseSize(visualSize)"),
                "Visual scale should follow the same bounded speed factor");
        assertTrue(source.contains("selectSprintSlashTarget(player, range)"),
                "Real hit range must be handled separately from visual BaseSize");
    }

    @Test
    void movementIsBoundToTheSameBladeAndClearedOnPlayerReplacement() throws IOException {
        String source = Files.readString(MOVEMENT_SOURCE);

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
        String source = Files.readString(MOVEMENT_SOURCE);

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
