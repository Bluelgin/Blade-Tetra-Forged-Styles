package dev.btsealed.mixin;

import dev.btsealed.runtime.PortableHabakiKnockback;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Use an independent before/after scope instead of a conflicting @Redirect.
 * This can coexist with Blade Tetra's original native melee redirect.
 */
@Mixin(value = AttackManager.class, remap = false)
public abstract class PortableHabakiMeleeMixin {
    @Inject(method = "doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("HEAD"))
    private static void btSealed$beginMelee(LivingEntity user, Entity target,
            boolean forceHit, boolean resetHit, float damage, CallbackInfo ci) {
        PortableHabakiKnockback.beginMelee(user, target);
    }

    @Inject(method = "doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("RETURN"))
    private static void btSealed$endMelee(LivingEntity user, Entity target,
            boolean forceHit, boolean resetHit, float damage, CallbackInfo ci) {
        PortableHabakiKnockback.endMelee();
    }
}
