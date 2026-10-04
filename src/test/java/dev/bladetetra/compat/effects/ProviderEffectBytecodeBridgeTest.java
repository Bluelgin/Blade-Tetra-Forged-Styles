package dev.bladetetra.compat.effects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class ProviderEffectBytecodeBridgeTest {
    private static final String CONCRETE = ProviderEffectBytecodeBridge.CONCRETE;
    private static final String CONTRACT = ProviderEffectBytecodeBridge.CONTRACT;
    private static final String GETTER_DESC =
            "(Lnet/minecraft/world/item/ItemStack;Lse/mickelus/tetra/effect/ItemEffect;)I";

    @Test void adaptsGateCastGetterAndFramesWithoutTouchingEffectConstants() {
        ClassNode node = fixture();
        MethodNode method = node.methods.get(0);
        FrameNode frame = new FrameNode(Opcodes.F_FULL, 1, new Object[]{CONCRETE}, 0, new Object[]{});
        method.instructions.add(frame);
        method.instructions.add(new LdcInsnNode(25));
        var result = ProviderEffectBytecodeBridge.adapt(node);
        assertEquals(1, result.adapted());
        assertEquals(0, result.unsupported());
        assertInstanceOf(MethodInsnNode.class, method.instructions.get(0));
        assertEquals(CONTRACT, ((TypeInsnNode) method.instructions.get(1)).desc);
        MethodInsnNode getter = (MethodInsnNode) method.instructions.get(2);
        assertEquals(Opcodes.INVOKEINTERFACE, getter.getOpcode());
        assertTrue(getter.itf);
        assertEquals(CONTRACT, getter.owner);
        assertEquals(CONTRACT, frame.local.get(0));
        assertEquals(25, ((LdcInsnNode) method.instructions.getLast()).cst);
        assertEquals(0, ProviderEffectBytecodeBridge.adapt(node).adapted(), "idempotent");
    }

    @Test void unknownConcreteMethodLeavesWholeMethodUntouched() {
        ClassNode node = fixture();
        node.methods.get(0).instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                CONCRETE, "unknownFutureMethod", "()V", false));
        var result = ProviderEffectBytecodeBridge.adapt(node);
        assertEquals(0, result.adapted());
        assertEquals(1, result.unsupported());
        assertEquals(Opcodes.INSTANCEOF, node.methods.get(0).instructions.getFirst().getOpcode());
    }

    @Test void concreteArgumentEscapeIsNotPatched() {
        ClassNode node = fixture();
        node.methods.get(0).instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "other/Provider", "consume", "(L" + CONCRETE + ";)V", false));
        assertEquals(1, ProviderEffectBytecodeBridge.adapt(node).unsupported());
    }

    @Test void interfaceBasedProviderIsUnchanged() {
        ClassNode node = fixture();
        for (AbstractInsnNode instruction : node.methods.get(0).instructions) {
            if (instruction instanceof TypeInsnNode type) type.desc = CONTRACT;
            if (instruction instanceof MethodInsnNode call) {
                call.owner = CONTRACT;
                call.setOpcode(Opcodes.INVOKEINTERFACE);
                call.itf = true;
            }
        }
        assertEquals(0, ProviderEffectBytecodeBridge.adapt(node).adapted());
    }

    /** Explicit local audit only: real provider bytecode is never bundled in tests/releases. */
    @Test void auditedProviderBytecodeRoundTrips() throws Exception {
        String paths = System.getProperty("blade_tetra.providerEffectAuditJars");
        Assumptions.assumeTrue(paths != null && !paths.isBlank());
        int adapted = 0;
        for (String path : paths.split(";")) {
            try (JarFile jar = new JarFile(Path.of(path).toFile())) {
                for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                    if (!entry.getName().startsWith("com/mega/revelationfix/common/compat/tetra/effect/")
                            && !entry.getName().equals("com/vv/vvaddon/Handler/VVAddonEventHandler.class")
                            && !entry.getName().equals("com/inolia_zaicek/more_mod_tetra/Effect/IronSpell/MMTFreezeTetraEffect.class")
                            && !entry.getName().equals("com/inolia_zaicek/more_mod_tetra/Effect/IronSpell/MMTManaSiphonTetraEffect.class")) continue;
                    ClassNode node = new ClassNode();
                    new ClassReader(jar.getInputStream(entry)).accept(node, 0);
                    var result = ProviderEffectBytecodeBridge.adapt(node);
                    int expectedUnsupported = entry.getName().endsWith("VVAddonEventHandler.class") ? 2 : 0;
                    assertEquals(expectedUnsupported, result.unsupported(), entry.getName());
                    if (result.adapted() == 0) continue;
                    adapted += result.adapted();
                    ClassWriter writer = new ClassWriter(0);
                    node.accept(writer);
                    ClassNode roundTrip = new ClassNode();
                    new ClassReader(writer.toByteArray()).accept(roundTrip, 0);
                    assertEquals(0, ProviderEffectBytecodeBridge.adapt(roundTrip).adapted());
                    System.out.println("PROVIDER_EFFECT_AUDIT " + entry.getName() + " methods=" + result.adapted());
                }
            }
        }
        assertTrue(adapted >= 11, "expected real RevelationFix effect handlers");
    }

    private static ClassNode fixture() {
        ClassNode node = new ClassNode();
        node.name = "test/Provider";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "onHit", "()V", null, null);
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, CONCRETE));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, CONCRETE));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONCRETE,
                "getEffectLevel", GETTER_DESC, false));
        node.methods = new ArrayList<>(List.of(method));
        return node;
    }
}
