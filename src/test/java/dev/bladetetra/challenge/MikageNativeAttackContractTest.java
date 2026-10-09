package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect the pinned native dependency and enforce the boundary against manufactured VFX damage. */
class MikageNativeAttackContractTest {
    private static Set<String> calls(String type) throws Exception {
        Set<String> calls = new HashSet<>();
        try (var in = MikageNativeAttackContractTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean itf) { calls.add(method); }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }
    @Test void pinnedNativePipelineSelectsTargetsAndCallsRealHurt() throws Exception {
        assertTrue(calls("mods/flammpfeil/slashblade/util/AttackManager")
                .containsAll(Set.of("getTargettableEntitiesWithinAABB", "doManagedAttack", "attack", "hurt")));
        assertTrue(calls("mods/flammpfeil/slashblade/entity/EntityDrive").contains("hurt"));
        assertTrue(calls("mods/flammpfeil/slashblade/entity/EntityAbstractSummonedSword").contains("hurt"));
    }
    @Test void everyExecutionUsesOwnedNativeAttacksAndCannotWritePlayerHealth() throws Exception {
        for (String name : List.of("MikageSwordplayExecution", "MikageMoonEchoExecution", "MikageBoundaryExecution",
                "MikageThousandGatesExecution", "MikageGateCorridorExecution", "MikageGuardCounterExecution")) {
            String source = read(name);
            assertTrue(source.contains("MikageNativeCombat.run"), name);
            assertFalse(source.contains("dealTrialDamage"), name);
            assertFalse(source.contains("setHealth"), name);
            assertFalse(source.contains("setDamage(0"), name);
        }
        assertTrue(read("MikageGateBarrageExecution").contains("MikageNativeCombat.Rule"));
        assertTrue(read("MikageGateSwordEntity").contains("super.onHitEntity(hit)"));
        assertFalse(read("MikageCounterEvents").contains("setCanceled"));
    }
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/" + name + ".java"));
    }
}
