package dev.bladetetra.compat.effects;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Set;

/**
 * Narrow receiver adaptation, not an effect reimplementation. Audited providers
 * call interface-owned effect getters through the concrete ModularItem class.
 * Keep their event, probability, recursion tags and summoning code unchanged.
 */
public final class ProviderEffectBytecodeBridge {
    public static final String CONCRETE = "se/mickelus/tetra/items/modular/ModularItem";
    public static final String CONTRACT = "se/mickelus/tetra/items/modular/IModularItem";
    private static final String GUARD = "dev/bladetetra/compat/effects/ProviderEffectEligibility";
    private static final String ARGUMENTS = "(Lnet/minecraft/world/item/ItemStack;Lse/mickelus/tetra/effect/ItemEffect;)";
    private static final Set<String> GETTERS = Set.of(
            "getEffectLevel" + ARGUMENTS + "I", "getEffectEfficiency" + ARGUMENTS + "F");

    public record Result(int adapted, int unsupported) {}

    public static Result adapt(ClassNode target) {
        int adapted = 0;
        int unsupported = 0;
        for (MethodNode method : target.methods) {
            boolean gate = false;
            boolean cast = false;
            boolean getter = false;
            boolean unsafe = method.desc.contains(CONCRETE);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type && CONCRETE.equals(type.desc)) {
                    gate |= type.getOpcode() == Opcodes.INSTANCEOF;
                    cast |= type.getOpcode() == Opcodes.CHECKCAST;
                    unsafe |= type.getOpcode() != Opcodes.INSTANCEOF && type.getOpcode() != Opcodes.CHECKCAST;
                } else if (instruction instanceof MethodInsnNode call) {
                    if (CONCRETE.equals(call.owner)) {
                        boolean supported = call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && GETTERS.contains(call.name + call.desc);
                        getter |= supported;
                        unsafe |= !supported;
                    }
                    unsafe |= call.desc.contains(CONCRETE);
                } else if (instruction instanceof FieldInsnNode field) {
                    unsafe |= CONCRETE.equals(field.owner) || field.desc.contains(CONCRETE);
                }
            }
            if (!gate || !cast || !getter) continue;
            // Unknown versions retain their original method; never partially patch it.
            if (unsafe) {
                unsupported++;
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof TypeInsnNode type && CONCRETE.equals(type.desc)) {
                    if (type.getOpcode() == Opcodes.INSTANCEOF) {
                        method.instructions.set(type, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                GUARD, "accepts", "(Ljava/lang/Object;)Z", false));
                    } else {
                        type.desc = CONTRACT;
                    }
                } else if (instruction instanceof MethodInsnNode call && CONCRETE.equals(call.owner)) {
                    call.owner = CONTRACT;
                    call.setOpcode(Opcodes.INVOKEINTERFACE);
                    call.itf = true;
                } else if (instruction instanceof FrameNode frame) {
                    adaptFrame(frame.local);
                    adaptFrame(frame.stack);
                }
            }
            if (method.localVariables != null) {
                for (LocalVariableNode local : method.localVariables) {
                    if (("L" + CONCRETE + ";").equals(local.desc)) {
                        local.desc = "L" + CONTRACT + ";";
                        local.signature = null;
                    }
                }
            }
            adapted++;
        }
        return new Result(adapted, unsupported);
    }

    private static void adaptFrame(List<Object> values) {
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            if (CONCRETE.equals(values.get(i))) values.set(i, CONTRACT);
        }
    }

    private ProviderEffectBytecodeBridge() {}
}
