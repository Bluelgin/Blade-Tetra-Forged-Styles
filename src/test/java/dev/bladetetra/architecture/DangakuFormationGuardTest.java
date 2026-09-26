package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural guards for Dangaku's position-first control flow. */
class DangakuFormationGuardTest {
    private static final Path FORMATION_SOURCE = Path.of(
            "src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java");
    private static final Path STYLE_SOURCE = Path.of(
            "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java");

    @Test
    void rightClickIsCapturedBeforeNativeProgressCombo() throws IOException {
        String combos = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ModComboStates.java"));
        String buffer = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/StyleInputBuffer.java"));
        String handler = Files.readString(FORMATION_SOURCE);

        assertTrue(combos.contains("DANGAKU_CHARGED_SWEEP_ID"),
                "Charged sweep should be an explicit visual combo node");
        assertTrue(combos.contains("if (commands.contains(InputCommand.L_CLICK))"),
                "Left click should deliberately select Dangaku cleave");
        assertFalse(buffer.contains("BladeStyle.DANGAKU"),
                "The old automatic cleave/sweep input buffer must not fight held right-click");

        assertTrue(handler.contains("PlayerInteractEvent.RightClickItem"),
                "Plain Dangaku right-click must be intercepted before ItemSlashBlade.use");
        assertTrue(handler.contains("event.setCancellationResult(InteractionResult.SUCCESS)"),
                "Captured right-click must terminate the native item-use dispatch cleanly");
        assertTrue(handler.contains("player.startUsingItem(hand);"),
                "Dangaku should enter the held-use state without calling native progressCombo");
        assertTrue(handler.contains("if (!isNeutralForCharge(player, blade))"),
                "Held right-click should wait for recovery before beginning item use");
        assertTrue(handler.indexOf("if (!isNeutralForCharge(player, blade))")
                        < handler.indexOf("player.startUsingItem(hand);"),
                "Recovery must not start a doomed charge or accrue charge time");
        assertTrue(handler.contains("event.setCancellationResult(InteractionResult.FAIL)"),
                "Blocked use should let the client retry held input after recovery");
        assertTrue(handler.contains("|| !state.armed()"),
                "A captured but locked right-click must never leak into a native release action");
        assertFalse(handler.contains("onChargeStart(LivingEntityUseItemEvent.Start"),
                "Charge ownership should come from the pre-use right-click interceptor, not a late Start event");
    }

    @Test
    void groundedChargeCannotBeCarriedIntoAerialRelease() throws IOException {
        String source = Files.readString(FORMATION_SOURCE);

        assertTrue(source.contains("charging.armed() && !player.onGround()"),
                "Leaving the ground after a captured charge must permanently disarm that release");
        assertTrue(source.contains("new ChargeState(charging.blade(), charging.startedAt(), false)"),
                "Airborne charge invalidation should stay transient and reuse the existing charge state");
        assertTrue(source.contains("!state.armed()"),
                "The later Stop event must be canceled instead of leaking into native releaseUsing");
    }

    @Test
    void chargeHasOnePeakThenDecaysAndKeepsLargeBoundedRange() throws IOException {
        String math = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/DangakuChargeMath.java"));

        assertTrue(math.contains("FULL_CHARGE_TICKS = 32"),
                "Dangaku should take about 1.6 seconds to reach peak charge");
        assertTrue(math.contains("PEAK_GRACE_TICKS = 8"),
                "Peak release needs a small human reaction window");
        assertTrue(math.contains("MIN_DECAYED_CHARGE = 0.25D"),
                "Overholding should decay without cycling back to another peak");
        assertTrue(math.contains("CHARGED_RANGE_BASE = 8.50D"),
                "Even a low-panel full charge should feel meaningfully large");
        assertTrue(math.contains("CHARGED_RANGE_MAX = 12.0D"),
                "Panel scaling must remain hard-capped");
        assertTrue(math.contains("DAMAGE_RATIO_FLOOR = 0.30F"),
                "Dangaku charged sweep should prioritize control over raw damage");
        assertTrue(math.contains("DAMAGE_RATIO_MAX = 0.48F"),
                "Even a high-panel full charge should remain below the old damage ceiling");
        assertTrue(math.contains("Math.sqrt(Math.max(1.0D, panelDamage)"),
                "Panel scaling should be soft rather than linear runaway");
        assertFalse(math.contains("SHORT_PRESS_TICKS"),
                "Tap/SA arbitration should follow the blade's native full-charge threshold, not a duplicate constant");
    }

    @Test
    void slashArtOverlaysDangakuSweepWithoutReimplementingSa() throws IOException {
        String source = Files.readString(FORMATION_SOURCE);

        assertTrue(source.contains("boolean slashArtRelease = canReleaseSlashArt"),
                "Dangaku should treat native SA as an overlay flag rather than an exclusive release branch");
        assertTrue(source.contains("new PendingStrike("),
                "Charged formation sweep should be scheduled regardless of SA overlay");
        assertTrue(source.contains("slashArtRelease));"),
                "Pending sweep should remember only whether SA is sharing the release");
        assertTrue(source.contains("if (!slashArtRelease)"),
                "Only non-SA releases should take over the visual ComboState");
        assertTrue(source.contains("pending.withSlashArt()"),
                "SA overlay sweep should survive the native SA ComboState transition");
        assertTrue(source.contains("state.getFullChargeTicks(user)"),
                "Dangaku should reuse the blade's native SA threshold instead of hardcoding another timer");
        assertTrue(source.contains("SwordType.ENCHANTED"),
                "Only releases that native SlashBlade can actually treat as SA should remain uncanceled");
        assertFalse(source.contains("shouldYieldToSlashArt"),
                "SA should no longer replace Dangaku sweep through an exclusive yield branch");
        assertFalse(source.contains(".doChargeAction("),
                "Dangaku must not duplicate native SA timing, cost or ChargeActionEvent logic");
    }

    @Test
    void chargedSweepUsesHorizontalVisualAndBoundedNativeMelee() throws IOException {
        String source = Files.readString(FORMATION_SOURCE);

        assertTrue(source.contains("LivingEntityUseItemEvent.Stop"),
                "Dangaku release should arbitrate the captured held-use stop event");
        assertTrue(source.contains("event.setCanceled(true);"),
                "Non-SA releases must stop Resharped from also running a second release action");
        assertTrue(source.contains("effect.setRotationRoll(-10.0F);"),
                "Charged sweep visual should match the ordinary A1 horizontal sweep plane");
        assertFalse(source.contains("effect.setRotationRoll(90.0F);"),
                "The old 90-degree visual plane reads as a vertical cleave");
        assertTrue(source.contains("MAX_CHARGED_TARGETS = 24"),
                "Large sweep still needs a hard target budget");
        assertTrue(source.contains("SLASH_ART_SWEEP_DAMAGE_FACTOR = 0.55F"),
                "SA overlay sweep should deal reduced sidecar damage");
        assertTrue(source.contains("ORDINARY_SWEEP_DAMAGE_FACTOR = 0.58D"),
                "Ordinary sweep damage should stay secondary to formation control");
        assertTrue(source.contains("CLEAVE_DAMAGE_FACTOR = 0.80D"),
                "Dangaku cleave should be toned down from the previous multiplier");
        assertTrue(source.contains("AttackManager.doMeleeAttack(player, target, false, false, damageRatio)"),
                "Charged hits must preserve existing hurt windows");
        assertTrue(source.contains("TargetSelector.SlashBladeTargetingConditions"),
                "Charged sweep must preserve Resharped target legality");
        assertTrue(source.contains("PENDING_STRIKES"),
                "The hit should land on the authored swing beat instead of release instantly");
        assertFalse(source.contains("getOrCreateTag()"),
                "Charge state should not be serialized to item NBT");
        assertFalse(source.contains("putLong("),
                "Charge state should stay transient rather than becoming player/target NBT");
    }

    @Test
    void chargedSweepSidecarCannotBorrowSaHitEffectOrDurability() throws IOException {
        String source = Files.readString(FORMATION_SOURCE);

        assertTrue(source.contains("CHARGED_SWEEP_HIT_CONTEXT"),
                "Charged sidecar hits need an exact synchronous context");
        assertTrue(source.contains("onChargedSweepHitResolved(SlashBladeEvent.HitEvent event)"),
                "The post-damage/pre-hitEffect boundary must be owned explicitly");
        assertTrue(source.contains("event.getBlade() != context.blade"));
        assertTrue(source.contains("event.getTarget() != context.target"));
        assertTrue(source.contains("event.setCanceled(true)"),
                "Canceling only the exact sidecar HitEvent prevents current-SA hitEffect and durability reuse");
        assertTrue(source.contains("CHARGED_SWEEP_HIT_CONTEXT.remove()"),
                "The context must not leak past its synchronous melee call");
    }

    @Test
    void formationPullSynchronizesPlayersAndLegacyStateIsReadOnlyMigration() throws IOException {
        String source = Files.readString(FORMATION_SOURCE);
        String style = Files.readString(STYLE_SOURCE);
        String hud = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/DangakuChargeHud.java"));

        assertTrue(source.contains("CLUSTER_REQUIRED_TARGETS = 3"),
                "Cleave payoff should read the current enemy formation");
        assertTrue(source.contains("CLUSTER_DAMAGE_MULTIPLIER = 1.06F"),
                "Cluster reward should remain a modest positional payoff");
        assertTrue(source.contains("clearLegacyBrokenStance"),
                "Old save tags may still be removed during migration");
        assertTrue(source.contains("pullTowardFocus"),
                "Sweep identity should come from enemy repositioning");
        assertTrue(source.contains("ClientboundSetEntityMotionPacket"),
                "ServerPlayer pull must explicitly synchronize motion");
        assertTrue(source.contains("target.hurtMarked = true"),
                "Non-player living targets should also publish their new motion");

        assertFalse(style.contains("BROKEN_STANCE_OWNER"),
                "Legacy Dangaku broken-stance state must no longer be written by active combat");
        assertFalse(style.contains("applyBrokenStance("),
                "The obsolete +5% timer must not remain in the active damage pipeline");

        assertTrue(hud.contains("RenderGuiEvent.Post"),
                "Charge needs an in-world HUD instead of chat spam");
        assertTrue(hud.contains("DangakuChargeMath.chargeForHeldTicks"),
                "Client display and server release should share one charge formula");
        assertTrue(hud.contains("getTicksUsingItem()"),
                "HUD progress should come from the native held-use timer");
    }
}
