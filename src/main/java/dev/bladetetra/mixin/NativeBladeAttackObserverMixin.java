package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.NativeBladeActionObserver;
import net.minecraft.world.entity.Entity;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackManager", remap = false)
public abstract class NativeBladeAttackObserverMixin {
    @ModifyVariable(method = "doManagedAttack(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Consumer<Entity> bladeTetra$observe(Consumer<Entity> attack) {
        return NativeBladeActionObserver.observe(attack);
    }
}
