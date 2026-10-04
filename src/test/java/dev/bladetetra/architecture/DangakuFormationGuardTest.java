package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DangakuFormationGuardTest {
    @Test void formationDoesNotInterceptRightClickOrStackDamageOnSa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java"));
        for (String removed : new String[]{"PlayerInteractEvent", "LivingEntityUseItemEvent", "doMeleeAttack", "PENDING_STRIKES", "onPlayerTick"})
            assertFalse(source.contains(removed), removed);
        assertTrue(source.contains("event.isCanceled()"));
        assertTrue(source.contains("KNOCKBACK_RESISTANCE"));
        assertTrue(source.contains("ClientboundSetEntityMotionPacket"));
        assertTrue(source.contains("target.hurtMarked = true"));
    }
    @Test void noLegacyChargeHudOrPerTargetMarkWriterRemains() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/dev/bladetetra/client/DangakuChargeHud.java")));
        String source = Files.readString(Path.of("src/main/java/dev/bladetetra/combat/DangakuFormationHandler.java"));
        assertTrue(source.contains("data.remove(\"blade_tetra_broken_stance_owner\")"));
        assertFalse(source.contains("putLong("));
        assertFalse(source.contains("putString("));
    }
    @Test void landingCannotAccumulateHeightDamageAndSaVisualsAreNotResized() throws Exception {
        String runtime = Files.readString(Path.of("src/main/java/dev/bladetetra/combat/StyleBranchRuntime.java"));
        assertFalse(runtime.contains("areaAttack"));
        assertFalse(runtime.contains("doMeleeAttack"));
        assertTrue(runtime.contains("Phase.D_LAND"));
        assertTrue(runtime.contains("Math.max(-1.2"));
        String style = Files.readString(Path.of("src/main/java/dev/bladetetra/combat/DangakuStyleCombat.java"));
        assertTrue(style.contains("if (BranchingStyleCombos.phase(combo) == null) return color"));
    }
}
