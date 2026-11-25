package dev.btsealed.mixin;

import dev.btsealed.runtime.PortableHabakiKnockback;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject around vanilla knockback rather than redirecting its movement write.
 * Source Blade Tetra's redirect and the standalone runtime can coexist.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class PortableHabakiLivingMixin {
    @Group(name = "btSealed$beginKnockback", min = 1)
    @Inject(method = "knockback(DDD)V", at = @At("HEAD"), require = 0)
    private void btSealed$beginNamed(double strength, double x, double z, CallbackInfo ci) {
        PortableHabakiKnockback.beforeKnockback((LivingEntity) (Object) this);
    }

    @Group(name = "btSealed$beginKnockback")
    @Inject(method = "m_147240_(DDD)V", at = @At("HEAD"), require = 0)
    private void btSealed$beginProduction(double strength, double x, double z, CallbackInfo ci) {
        PortableHabakiKnockback.beforeKnockback((LivingEntity) (Object) this);
    }

    @Group(name = "btSealed$endKnockback", min = 1)
    @Inject(method = "knockback(DDD)V", at = @At("RETURN"), require = 0)
    private void btSealed$endNamed(double strength, double x, double z, CallbackInfo ci) {
        PortableHabakiKnockback.afterKnockback((LivingEntity) (Object) this);
    }

    @Group(name = "btSealed$endKnockback")
    @Inject(method = "m_147240_(DDD)V", at = @At("RETURN"), require = 0)
    private void btSealed$endProduction(double strength, double x, double z, CallbackInfo ci) {
        PortableHabakiKnockback.afterKnockback((LivingEntity) (Object) this);
    }
}
