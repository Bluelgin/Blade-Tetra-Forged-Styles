package dev.bladetetra.mixin;

import mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resharped 1.9.63 dereferences an unfired heavy-rain sword's owner on the
 * client. Orphaned saved projectiles from a previous session therefore crash
 * before the server has time to send their removal packet.
 */
@Mixin(value = EntityHeavyRainSwords.class, remap = false)
public abstract class HeavyRainSwordSafetyMixin {
    @Inject(
            method = { "tick", "m_8119_" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void bladeTetra$discardClientOrphan(CallbackInfo callback) {
        EntityHeavyRainSwords self = (EntityHeavyRainSwords) (Object) this;
        if (self.level().isClientSide && !self.itFired()
                && self.getVehicle() == null && self.getOwner() == null) {
            self.discard();
            callback.cancel();
        }
    }
}
