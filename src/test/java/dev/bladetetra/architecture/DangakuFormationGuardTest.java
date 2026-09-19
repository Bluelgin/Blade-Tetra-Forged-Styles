package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural guards for Dangaku's position-first control flow. */
class DangakuFormationGuardTest {
    @Test
    void rightClickIsCapturedBeforeNativeProgressCombo() throws IOException {
        String combos = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ModComboStates.java"));
        String buffer = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/StyleInputBuffer.java"));
        String handler = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java"));

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
        assertTrue(handler.contains("boolean armed = isNeutralForCharge"),
                "Rapid clicks during a locked attack should be swallowed rather than animation-cancel spam");
        assertTrue(handler.contains("|| !state.armed()"),
                "A captured but locked right-click must never leak into a native release action");
        assertFalse(handler.contains("onChargeStart(LivingEntityUseItemEvent.Start"),
                "Charge ownership should come from the pre-use right-click interceptor, not a late Start event");
    }

    @Test
    void chargeHasOnePeakThenDecaysAndKeepsLargeBoundedRange() throws IOException {
        String math = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/DangakuChargeMath.java"));

        assertTrue(math.contains("FULL_CHARGE_TICKS = 44"),
                "Dangaku should take about 2.2 seconds to reach peak charge");
        assertTrue(math.contains("PEAK_GRACE_TICKS = 5"),
                "Peak release needs a small human reaction window");
        assertTrue(math.contains("MIN_DECAYED_CHARGE = 0.25D"),
                "Overholding should decay without cycling back to another peak");
        assertTrue(math.contains("CHARGED_RANGE_BASE = 8.50D"),
                "Even a low-panel full charge should feel meaningfully large");
        assertTrue(math.contains("CHARGED_RANGE_MAX = 12.0D"),
                "Panel scaling must remain hard-capped");
        assertTrue(math.contains("Math.sqrt(Math.max(1.0D, panelDamage)"),
                "Panel scaling should be soft rather than linear runaway");
    }

    @Test
    void chargedSweepUsesHorizontalVisualAndBoundedNativeMelee() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java"));

        assertTrue(source.contains("LivingEntityUseItemEvent.Stop"),
                "Dangaku release should own the captured held-use stop event");
        assertTrue(source.contains("event.setCanceled(true);"),
                "Owned release must not also trigger Resharped's Slash-Art release path");
        assertTrue(source.contains("effect.setRotationRoll(-10.0F);"),
                "Charged sweep visual should match the ordinary A1 horizontal sweep plane");
        assertFalse(source.contains("effect.setRotationRoll(90.0F);"),
                "The old 90-degree visual plane reads as a vertical cleave");
        assertTrue(source.contains("MAX_CHARGED_TARGETS = 24"),
                "Large sweep still needs a hard target budget");
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
    void positionReplacesLegacyBrokenStanceAndHudSharesChargeMath() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java"));
        String hud = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/DangakuChargeHud.java"));

        assertTrue(source.contains("CLUSTER_REQUIRED_TARGETS = 3"),
                "Cleave payoff should read the current enemy formation");
        assertTrue(source.contains("clearLegacyBrokenStance"),
                "The obsolete +5% broken-stance tags should be neutralized during migration");
        assertTrue(source.contains("pullTowardFocus"),
                "Sweep identity should come from enemy repositioning");
        assertTrue(hud.contains("RenderGuiEvent.Post"),
                "Charge needs an in-world HUD instead of chat spam");
        assertTrue(hud.contains("DangakuChargeMath.chargeForHeldTicks"),
                "Client display and server release should share one charge formula");
        assertTrue(hud.contains("getTicksUsingItem()"),
                "HUD progress should come from the native held-use timer");
    }
}
