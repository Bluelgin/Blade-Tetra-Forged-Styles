package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the actual locked dependency contract, without booting a Minecraft world. */
class SlashBladeLifecycleContractTest {
    private static Set<String> calls(String resource, String method) throws Exception {
        Set<String> calls = new HashSet<>();
        try (InputStream bytes = SlashBladeLifecycleContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (bytes == null) throw new AssertionError("Missing dependency bytecode: " + resource);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (method != null && !method.equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                String descriptor, boolean isInterface) {
                            calls.add(name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return calls;
    }

    @Test void updateComboReallyCommitsClockAndClickAndHonorsCancellation() throws Exception {
        var calls = calls("mods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState.class", "updateComboSeq");
        assertTrue(calls.containsAll(Set.of("post", "isCanceled", "setComboSeq", "setLastActionTime", "clickAction")));
    }

    @Test void inventoryOwnsTicksAndTimeoutResolutionOwnsFurtherCommits() throws Exception {
        // The inventory lambda has an obfuscated/generated name; scan the class, not a guessed name.
        assertTrue(calls("mods/flammpfeil/slashblade/item/ItemSlashBlade.class", null)
                .containsAll(Set.of("resolvCurrentComboState", "tickAction")));
        assertTrue(calls("mods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState.class",
                "resolvCurrentComboStateTicks")
                .containsAll(Set.of("getTimeoutMS", "getNextOfTimeout", "updateComboSeq")));
    }
}
