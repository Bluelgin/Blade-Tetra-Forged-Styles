package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageNativeCombat;
import net.minecraft.world.entity.Entity;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackManager", remap = false)
public abstract class MikageNativeManagedAttackMixin {
    @Inject(method = "doManagedAttack", at = @At("HEAD"), cancellable = true)
    private static void bladeTetra$nativeImmunity(Consumer<Entity> attack, Entity target,
            boolean forceHit, boolean resetHit, CallbackInfo ci) {
        if (MikageNativeCombat.managed(attack, target)) ci.cancel();
    }
}
