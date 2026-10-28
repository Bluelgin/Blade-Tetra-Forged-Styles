package dev.bladetetra.kyouka;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Exact native commit-site observer. No native instructions removed/reordered, no callbacks replayed. */
public final class NativeBladeCommitBridge {
    public static void install(ClassNode node) {
        String state = "mods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState";
        for (MethodNode method : node.methods) {
            if (!method.name.equals("updateComboSeq") || !method.desc.equals(
                    "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/resources/ResourceLocation;)V")) continue;
            MethodInsnNode commit = null;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(state)
                        && call.name.equals("setLastActionTime") && call.desc.equals("(J)V")) {
                    if (commit != null) throw new IllegalStateException("Ambiguous native combo commit site");
                    commit = call;
                }
            }
            if (commit == null) throw new IllegalStateException("Missing native combo commit site");
            InsnList observer = new InsnList();
            observer.add(new VarInsnNode(Opcodes.ALOAD, 1));
            observer.add(new VarInsnNode(Opcodes.ALOAD, 0));
            observer.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "dev/bladetetra/kyouka/NativeBladeActionObserver", "committed",
                    "(Lnet/minecraft/world/entity/LivingEntity;L"+state+";)V", false));
            method.instructions.insert(commit, observer);
            method.maxStack = Math.max(method.maxStack, 2);
            return;
        }
        throw new IllegalStateException("Missing native updateComboSeq method");
    }
    private NativeBladeCommitBridge() {}
}
