package dev.bladetetra.mixin;

import dev.bladetetra.combat.HabakiKnockback;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class HabakiLivingKnockbackMixin {
    @Group(name = "bladeTetra$knockback", min = 1, max = 1)
    @Redirect(method = "knockback(DDD)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"), require = 0)
    private void bladeTetra$applyImpulse(LivingEntity target, double x, double y, double z) {
        HabakiKnockback.applyImpulse(target, x, y, z);
    }
    @Group(name = "bladeTetra$knockback")
    @Redirect(method = "m_147240_(DDD)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;m_20334_(DDD)V"), require = 0)
    private void bladeTetra$applyProductionImpulse(LivingEntity target, double x, double y, double z) {
        HabakiKnockback.applyImpulse(target, x, y, z);
    }
}
