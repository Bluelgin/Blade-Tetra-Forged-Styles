package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Native Rengeki rollback retains sprint visuals/damage without restoring automatic pursuit. */
class RengekiShortStepGuardTest {
    private String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/combat/" + file + ".java"));
    }
    @Test void sprintReturnsWithoutAnyAutomaticKillTeleport() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/dev/bladetetra/combat/RengekiShortStepHandler.java")));
        String sprint = source("RengekiMomentumHandler");
        String state = source("RengekiRuntimeState");
        for (String removed : new String[]{"RengekiShortStepHandler", "killTransfers()", "chaseWindows()",
                "teleportTo(", "tryMoveToTarget", "scheduleKillTransfer"}) {
            assertFalse(sprint.contains(removed), removed);
            assertFalse(state.contains(removed), removed);
        }
        assertTrue(sprint.contains("RengekiFlowRules.SPRINT_DAMAGE_RATIO"));
        assertTrue(sprint.contains("SPRINT_HIT_INTERVAL_TICKS = 4"));
        assertTrue(sprint.contains("AttackManager.doMeleeAttack("));
        assertFalse(sprint.contains("SPRINT_SLASH_MAX_DAMAGE_RATIO"));
        assertTrue(sprint.contains("CombatBalanceRuntime.ordinaryCombo(player)"));
    }
    @Test void liveRengekiUsesNativeBInsteadOfTheReplacementShortChain() throws Exception {
        String graph = source("ModComboStates");
        String opener = graph.substring(graph.indexOf("private static ResourceLocation selectRengekiOpener"),
                graph.indexOf("private static ResourceLocation selectDangakuOpener"));
        assertTrue(opener.contains("selectOpener(entity, ComboStateRegistry.COMBO_B1.getId())"));
        assertFalse(opener.contains("BranchingStyleCombos.opener"));
    }
    @Test void nativeTimelinesAreReusedButDamagingRecoveriesAndHoldLoopsAreNot() throws Exception {
        String adapter = source("BranchingStyleCombos");
        assertTrue(adapter.contains("original::tickAction"));
        assertTrue(adapter.contains("original::hitEffect"));
        assertTrue(adapter.contains("original::clickAction"));
        assertFalse(adapter.contains("original::holdAction"));
        assertFalse(adapter.contains("original::releaseAction"));
        assertFalse(adapter.contains("ComboStateRegistry.COMBO_B1_END;"));
        assertTrue(adapter.contains("ComboStateRegistry.COMBO_B_END2"));
        assertTrue(adapter.contains("else if (!phase.recovery())"));
    }
    @Test void aerialRightClickIsNotBlockedByAPerJumpQuota() throws Exception {
        String runtime = source("StyleBranchRuntime");
        assertFalse(runtime.contains("AirBudget"));
        assertFalse(runtime.contains("chainUsed"));
        assertFalse(runtime.contains("diveUsed"));
        assertFalse(source("BranchingStyleCombos").contains("canStartAir"));
        assertTrue(runtime.contains("finishIfLanded"));
        assertFalse(runtime.contains("getOrCreateTag"));
        assertFalse(runtime.contains("AttackManager.areaAttack"));
    }
    @Test void aerialUsesGroundCallbacksAndNativeSlowFallingExactlyOnce() throws Exception {
        String aerial = source("StyleAerialAttacks");
        assertTrue(aerial.contains("original.tickAction(entity)"));
        assertTrue(aerial.contains("FallHandler.fallDecrease(entity)"));
        assertTrue(aerial.contains("if (!original.isAerial())"));
        assertFalse(aerial.contains("AttackManager"));
        assertFalse(aerial.contains("areaAttack"));
        assertTrue(source("StyleCommonCommands").contains("ComboStateRegistry.AERIAL_CLEAVE.getId()"));
    }
    @Test void queuedClickBelongsToExactBladeAndComboStart() throws Exception {
        String buffer = source("StyleInputBuffer");
        assertTrue(buffer.contains("stack != click.blade()"));
        assertTrue(buffer.contains("state.getLastActionTime() != click.startedAt()"));
        assertTrue(buffer.contains("PENDING.putIfAbsent"));
        assertTrue(buffer.contains("state.getFullChargeTicks(entity)"));
    }
    @Test void masteryAndTechniqueKnowTheNewFinisher() throws Exception {
        assertTrue(source("BladeTechniqueHandler").contains("Phase.R_FINISH"));
        assertTrue(source("RustReleaseFusionHandler").contains("Phase" ) || source("RustReleaseFusionHandler").contains("BranchingStyleCombos.phase"));
        assertTrue(Files.readString(Path.of("src/main/java/dev/bladetetra/easteregg/BladeLegacyEasterEggs.java"))
                .contains("BranchingStyleCombos.rengekiFinisher"));
    }
    @Test void airHeavyFinisherCannotAlsoTriggerGroundOnlyTechniques() throws Exception {
        String technique = source("BladeTechniqueHandler");
        String ground = technique.substring(technique.indexOf("private static boolean isGroundFinisher"),
                technique.indexOf("private static boolean isAerialFinisher"));
        assertTrue(ground.contains("Phase.D_HEAVY"));
        assertFalse(ground.contains("isDangakuCleave"));
        assertFalse(ground.contains("D_AIR_HEAVY"));
        assertTrue(technique.contains("Phase.D_AIR_HEAVY"));
    }
}
