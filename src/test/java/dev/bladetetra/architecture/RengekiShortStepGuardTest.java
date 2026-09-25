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
    void pursuitUsesAuthoritativeRightClickBAdvances() throws IOException {
        String source = Files.readString(MOVEMENT_SOURCE);

        assertTrue(source.contains("BladeMotionEvent"),
                "Rengeki pursuit must hook Resharped's actual combo transition");
        assertFalse(source.contains("InputCommandEvent"),
                "Raw MoveInput synchronization does not carry transient click intent");
        assertFalse(source.contains("InputCommand.L_CLICK"),
                "Ordinary pursuit should not opt into left-click movement");
        assertTrue(source.contains("InputCommand.R_CLICK"),
                "Right-click pursuit must read Resharped's transient R_CLICK");
        assertTrue(source.contains("player.getCapability(ItemSlashBlade.INPUT_STATE)"),
                "Click intent must come from Resharped's authoritative input capability");
        assertTrue(source.contains("boolean rightClickAdvance = expectedBAdvance && isRightClickAdvance(player)"),
                "Only a real right-click B advance may consume ordinary pursuit as movement");

        assertAdvance(source, "COMBO_B1", "COMBO_B2");
        assertAdvance(source, "COMBO_B2", "COMBO_B3");
        assertAdvance(source, "COMBO_B3", "COMBO_B4");
        assertAdvance(source, "COMBO_B4", "COMBO_B5");
        assertAdvance(source, "COMBO_B5", "COMBO_B6");
        assertAdvance(source, "COMBO_B6", "COMBO_B7");
    }

    @Test
    void nativeBTradeoffIncludesAuthoredRecoveryButNotOtherBranches() throws IOException {
        String movement = Files.readString(MOVEMENT_SOURCE);
        String momentum = Files.readString(MOMENTUM_SOURCE);

        assertTrue(momentum.contains("NATIVE_B_DAMAGE_MULTIPLIER = 0.85D"),
                "Native B should trade 15% slash damage for expanded flow tools");
        assertTrue(momentum.contains("RengekiShortStepHandler.isNativeBFlowState"),
                "The damage tradeoff must stay scoped to the native B flow");
        assertTrue(momentum.contains("event.setDamage(event.getDamage() * NATIVE_B_DAMAGE_MULTIPLIER)"),
                "Native B slash damage should be scaled exactly once at slash creation");

        assertTrue(movement.contains("ComboStateRegistry.COMBO_B1_END.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B1_END2.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B1_END3.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B_END.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B_END2.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B_END3.getId().equals(combo)"));
        assertTrue(movement.contains("ComboStateRegistry.COMBO_B7_END3.getId().equals(combo)"));
    }

    @Test
    void killHandoffIsAutomaticBoundedAndSurvivesPassiveRecovery() throws IOException {
        String source = Files.readString(MOVEMENT_SOURCE);

        assertTrue(source.contains("KILL_TRANSFER_DELAY_TICKS = 1"),
                "Kill hand-off must leave the current slash iteration before moving");
        assertTrue(source.contains("KILL_TRANSFER_SEARCH_DISTANCE = 6.5D"));
        assertTrue(source.contains("MAX_KILL_TRANSFER_DISTANCE = 4.5D"));
        assertTrue(source.contains("Math.cos(Math.toRadians(80.0D))"));
        assertTrue(source.contains("scheduleKillTransfer(player, event.getBlade())"));
        assertTrue(source.contains("public static void onPlayerTick(TickEvent.PlayerTickEvent event)"),
                "Kill continuity needs the one-tick automatic fallback");
        assertFalse(source.contains("killTransfer != null && rightClickAdvance"),
                "Kill hand-off must remain independent from ordinary right-click pursuit");

        assertTrue(source.contains("isPassiveBFlowTransition(current, next)"),
                "A delayed kill must not be erased by a same-tick B recovery transition");
        assertTrue(source.contains("return isNativeBFlowState(current)"),
                "Only a transition originating in the native B flow may preserve the pending hand-off");
        assertTrue(source.contains("isNativeBFlowState(next)"),
                "Native B recovery-to-recovery transitions should preserve the hand-off");
        assertTrue(source.contains("ComboStateRegistry.NONE.getId().equals(next)"),
                "A passive recovery timeout to NONE should still allow the next-tick hand-off fallback");
    }

    @Test
    void sprintFlowIsIdleGroundedAndSeparatesVisualFromDamageCadence() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_VISUAL_INTERVAL_TICKS = 10"),
                "B1-B7 visual beats should keep their ten-tick rhythm");
        assertTrue(source.contains("SPRINT_HIT_INTERVAL_TICKS = 4"),
                "Real sprint pressure should pulse every four ticks");
        assertTrue(source.contains("nextVisualBeatAt"));
        assertTrue(source.contains("nextHitAt"));
        assertTrue(source.contains("ComboStateRegistry.NONE.getId().equals(combo)"),
                "Sprint flow must never stack on an active real combo");
        assertTrue(source.contains("RengekiShortStepHandler.hasPendingKillTransfer(playerId)"),
                "Sprint flow must not race a pending kill hand-off");
        assertTrue(source.contains("!player.isSprinting()"));
        assertTrue(source.contains("!player.onGround()"));
        assertTrue(source.contains("player.isPassenger()"));
        assertTrue(source.contains("player.isUsingItem()"));
        assertTrue(source.contains("player.getAbilities().flying"));
    }

    @Test
    void sprintCadenceCannotBeResetSpamOrTeleportPowered() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("MAX_CONTINUOUS_SPRINT_DISPLACEMENT = 0.90D"),
                "Large movement discontinuities should never count as sprint speed");
        assertTrue(source.contains("distance > MAX_CONTINUOUS_SPRINT_DISPLACEMENT ? 0.0D : distance"),
                "Teleport/pursuit/large knockback deltas must be rejected by the speed sampler");
        assertTrue(source.contains("resetSprintVisuals(playerId);"),
                "Temporary eligibility loss should reset presentation only");
        assertFalse(source.contains("private static void resetSprintChain"),
                "Resetting the entire chain would recreate an immediately-ready hit cooldown");
        assertTrue(source.contains(
                "RengekiRuntimeState.SprintChainState chain = RengekiRuntimeState.sprintChains().get(playerId)"),
                "Visual reset should preserve the existing per-player cadence state");
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/RengekiRuntimeState.java"));
        assertTrue(runtime.contains("SPRINT_CHAINS.remove(playerId)"),
                "A full lifecycle/style clear must still discard the cooldown state");
    }

    @Test
    void sprintSpeedRangeDamageAndVisualsStayHardCapped() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_SLASH_MIN_SPEED = 0.12D"));
        assertTrue(source.contains("SPRINT_SLASH_SPEED_CAP = 0.36D"));
        assertTrue(source.contains("SPRINT_SLASH_MIN_RANGE = 1.35D"));
        assertTrue(source.contains("SPRINT_SLASH_MAX_RANGE = 2.75D"));
        assertTrue(source.contains("SPRINT_SLASH_MIN_DAMAGE_RATIO = 0.03F"));
        assertTrue(source.contains("SPRINT_SLASH_MAX_DAMAGE_RATIO = 0.07F"));
        assertTrue(source.contains("SPRINT_SLASH_MIN_VISUAL_SIZE = 0.28F"));
        assertTrue(source.contains("SPRINT_SLASH_MAX_VISUAL_SIZE = 0.58F"));
        assertTrue(source.contains("sampleHorizontalDisplacement(playerId, player.position(), now)"),
                "Scaling must use real server-tick displacement rather than friction-damped motion");
        assertTrue(source.contains("gameTime - previous.gameTime() != 1L"),
                "Non-consecutive samples must not fabricate movement speed");
    }

    @Test
    void sprintKeepsBVisualLanguageWithoutNativeAreaDamage() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("SPRINT_B_CHAIN_LENGTH = 7"));
        assertTrue(source.contains("SPRINT_B_BURST_TICKS = 7"));
        assertTrue(source.contains("chain.nextBeat = (chain.nextBeat + 1) % SPRINT_B_CHAIN_LENGTH"));
        assertTrue(source.contains("visualSize, -30.0F"));
        assertTrue(source.contains("visualSize, 145.0F"));
        assertTrue(source.contains("mirrored ? 90.0F : -90.0F"));
        assertTrue(source.contains("beat == SPRINT_B_CHAIN_LENGTH - 1"));
        assertTrue(source.contains("new EntitySlashEffect("));
        assertFalse(source.contains("effect.setOwner(player)"),
                "Visual-only slashes must remain ownerless and unable to run native areaAttack");
        assertFalse(source.contains("AttackManager.doSlash(player"),
                "Sprint visuals must not become owned native damage slashes");
    }

    @Test
    void sprintHitDoesNotErasePreexistingHurtWindows() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE).replace("\r\n", "\n");

        assertTrue(source.contains("int invulnerabilityBefore = target.invulnerableTime"),
                "The sprint hit must remember whether another source already owns the hurt window");
        assertTrue(source.contains("false,\n                    false,\n                    damageRatio"),
                "Neither forceHit nor resetHit may globally clear a target hurt window");
        assertFalse(source.contains("false,\n                    true,\n                    damageRatio"),
                "Resharped resetHit clears invulnerability even after a rejected hit and is unsafe here");
        assertTrue(source.contains("context.hitSucceeded && invulnerabilityBefore <= 0"),
                "Only a proven fresh sprint hit may shorten the window it just created");
        assertTrue(source.contains("target.invulnerableTime = Math.min("));
        assertTrue(source.contains("SPRINT_HIT_INTERVAL_TICKS"),
                "The fresh sprint window should align with the four-tick pulse rather than being zeroed");
    }

    @Test
    void sprintSuccessfulHitsConsumeNoDurabilityAndContextIsExact() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertFalse(source.contains("SPRINT_DURABILITY_DIVISOR"),
                "No-durability sprint hits need no divisor state");
        assertFalse(source.contains("SPRINT_DURABILITY_PHASE"),
                "No-durability sprint hits need no per-player phase map");
        assertTrue(source.contains("onSprintHitResolved(SlashBladeEvent.HitEvent event)"),
                "Successful sprint hits need a post-damage/pre-durability boundary");
        assertTrue(source.contains("priority = EventPriority.LOWEST, receiveCanceled = true"),
                "Other compatibility listeners should observe the hit before sprint cancels durability");
        assertTrue(source.contains("event.getTarget() != context.target"),
                "Nested HitEvents must not be mistaken for this sprint hit");
        assertTrue(source.contains("context.hitSucceeded = true"),
                "The same exact HitEvent should prove that melee damage succeeded");
        assertTrue(source.contains("if (!event.isCanceled())"));
        assertTrue(source.contains("event.setCanceled(true)"),
                "Canceling the exact successful HitEvent skips SlashBlade's later durability branch");
        assertFalse(source.contains("% SPRINT_DURABILITY_DIVISOR"));
    }

    @Test
    void sprintHitPreservesMomentumAndAvoidsAudioInterception() throws IOException {
        String source = Files.readString(MOMENTUM_SOURCE);

        assertTrue(source.contains("player.setSprinting(false)"),
                "Compatibility melee should not apply sprint knockback");
        assertTrue(source.contains("player.setDeltaMovement(momentum)"));
        assertTrue(source.contains("player.setSprinting(sprinting)"));
        assertTrue(source.contains("SPRINT_HIT_CONTEXT.remove()"),
                "Sprint context must not leak beyond the synchronous attack call");

        assertFalse(source.contains("PlayLevelSoundEvent"),
                "Sprint flow should not intercept Forge sound events");
        assertFalse(source.contains("SoundEvents.PLAYER_ATTACK_"),
                "Sprint flow should leave attack audio to the native combat path");
        assertFalse(source.contains("effect.setMute(true)"),
                "Visual slash audio should not carry a separate muting branch");
    }

    @Test
    void targetingAndMovementRemainBoundedAndLifecycleSafe() throws IOException {
        String movement = Files.readString(MOVEMENT_SOURCE);
        String momentum = Files.readString(MOMENTUM_SOURCE);

        assertTrue(movement.contains("TargetSelector.SlashBladeTargetingConditions"));
        assertTrue(movement.contains("TargetSelector.AttackablePredicate"));
        assertTrue(momentum.contains("TargetSelector.SlashBladeTargetingConditions"));
        assertTrue(momentum.contains("TargetSelector.AttackablePredicate"));
        assertTrue(movement.contains("player.hasLineOfSight(candidate)"));
        assertTrue(momentum.contains("player.hasLineOfSight(candidate)"));
        assertTrue(movement.contains("return player.onGround() && !player.isPassenger()"));
        assertTrue(movement.contains("isCollisionAreaLoaded(level, sampleBox)"));
        assertTrue(movement.contains("level.noCollision(player, sampleBox)"));

        String runtime = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/RengekiRuntimeState.java"));
        assertTrue(runtime.contains("PlayerEvent.Clone"),
                "Shared Rengeki runtime must clear both sub-features on clone");
        assertTrue(runtime.contains("clear(event.getOriginal().getUUID())"));
        assertTrue(runtime.contains("clearMomentum(playerId)"));
        assertTrue(runtime.contains("clearShortStep(playerId)"));
        assertFalse(momentum.contains("PlayerEvent.Clone"),
                "Momentum should not own duplicate lifecycle listeners after extraction");
        assertFalse(movement.contains("PlayerEvent.Clone"),
                "Short-step should not own duplicate lifecycle listeners after extraction");
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
