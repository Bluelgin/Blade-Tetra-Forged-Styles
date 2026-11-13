package dev.bladetetra.mixin;

import dev.bladetetra.combat.HabakiKnockback;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(value = AttackManager.class, remap = false)
public abstract class HabakiNativeMeleeMixin {
    @Redirect(method = "doMeleeAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/util/AttackManager;doManagedAttack(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;ZZ)V"))
    private static void bladeTetra$scopeMelee(Consumer<Entity> attack, Entity target, boolean forceHit,
            boolean resetHit, LivingEntity user) {
        HabakiKnockback.melee(user, target, () -> AttackManager.doManagedAttack(attack, target, forceHit, resetHit));
    }
}
