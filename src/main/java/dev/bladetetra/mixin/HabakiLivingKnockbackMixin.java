package dev.bladetetra.mixin;

import dev.bladetetra.combat.HabakiKnockback;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class HabakiLivingKnockbackMixin {
    @Redirect(method = "knockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void bladeTetra$applyImpulse(LivingEntity target, double x, double y, double z) {
        HabakiKnockback.applyImpulse(target, x, y, z);
    }
}
