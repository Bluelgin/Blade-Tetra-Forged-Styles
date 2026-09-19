package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrails for the native-B Rengeki pursuit integration. */
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
                "Canceled combo transitions must still clear a pending chase window");
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
    void terminalB7DoesNotArmAnotherChase() throws IOException {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("public static void onBladeHit");
        int end = source.indexOf("/**", start + 1);
        assertTrue(start >= 0 && end > start, "onBladeHit source block must remain discoverable");

        String hitHandler = source.substring(start, end);
        assertTrue(hitHandler.contains("canAdvanceBComboId"),
                "Only non-terminal B nodes should arm pursuit");
        assertFalse(hitHandler.contains("COMBO_B7"),
                "B7 is terminal and must not create a post-finisher teleport window");
    }

    @Test
    void pursuitIsBoundToTheSameBladeAndClearedOnPlayerReplacement() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("window.blade() != blade"),
                "A chase earned by one blade must not transfer to another Rengeki blade");
        assertTrue(source.contains("PlayerEvent.Clone"),
                "Death/respawn player replacement must clear transient pursuit state");
    }

    @Test
    void pursuitKeepsNativeTargetAndGroundSafety() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("TargetSelector.SlashBladeTargetingConditions"),
                "Pursuit must preserve Resharped's revenge-target and combat eligibility rules");
        assertTrue(source.contains("TargetSelector.AttackablePredicate"),
                "Pursuit targets must honor Resharped PVP/friendly targeting rules");
        assertTrue(source.contains("!player.onGround() || player.isPassenger()"),
                "Pursuit must not turn an airborne or riding B transition into a ground teleport");
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
