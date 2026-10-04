package dev.bladetetra.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DangakuSpinGuardTest {
    private String combat(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/combat/" + file + ".java"));
    }
    @Test void reusesNativeCircleInsteadOfIndependentMeleeOrVisualReplacements() throws Exception {
        String handler = combat("DangakuSpinHandler");
        assertTrue(handler.contains("nativeCircle.tickAction(entity)"));
        assertTrue(combat("DangakuSpinCombos").contains(".loop()"));
        assertFalse(handler.contains("EntityJoinLevelEvent"));
        assertFalse(handler.contains("setShooter(null)"));
        assertFalse(handler.contains("doMeleeAttack("));
        assertFalse(handler.contains("causeFoodExhaustion("));
        assertFalse(Files.exists(Path.of("src/main/java/dev/bladetetra/combat/DangakuSpinAttack.java")));
        assertTrue(handler.contains("!state.onClick()"));
    }
    @Test void noStunShieldOrIncomingKnockbackCancellation() throws Exception {
        String combos = combat("DangakuSpinCombos");
        assertFalse(combos.contains(".addHitEffect("));
        String handler = combat("DangakuSpinHandler");
        assertFalse(handler.contains("LivingHurtEvent"));
        assertFalse(handler.contains("setUntouchable"));
        assertFalse(handler.contains("setDeltaMovement"));
        assertTrue(combat("DangakuStyleCombat").contains("DangakuSpinRules.damageScale(event.isCritical())"));
    }
    @Test void ownershipAndCleanupAreTransientAndScopedToTheExactBlade() throws Exception {
        String handler = combat("DangakuSpinHandler");
        assertTrue(handler.contains("hold.blade != blade"));
        assertTrue(handler.contains("PlayerLoggedOutEvent"));
        assertTrue(handler.contains("PlayerChangedDimensionEvent"));
        assertTrue(handler.contains("ServerStoppedEvent"));
        assertTrue(handler.contains("addTransientModifier"));
        assertFalse(handler.contains("putBoolean(\"active"));
    }
}
