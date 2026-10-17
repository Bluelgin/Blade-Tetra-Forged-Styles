package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageNativeCombat;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.ability.StunManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

/** Preserve Drive's native ray, damage and enchantment hooks; authorize before any native side effect. */
@Mixin(value = EntityDrive.class, remap = false)
public abstract class MikageNativeDriveMixin {
    @Inject(method = "getRayTrace", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$authorizedRay(Vec3 from, Vec3 to, CallbackInfoReturnable<EntityHitResult> ci) {
        EntityDrive source = (EntityDrive) (Object) this;
        if (!MikageNativeCombat.authored(source)) return;
        // Identical native swept box and ProjectileUtil ray; filter outsiders before they can absorb a shot.
        ci.setReturnValue(ProjectileUtil.getEntityHitResult(source.level(), source, from, to,
                source.getBoundingBox().expandTowards(source.getDeltaMovement()).inflate(1), target ->
                !target.isSpectator() && target.isAlive() && target.isPickable() && target != source.getShooter()
                        && MikageNativeCombat.mayCollide(source, target)));
    }
    @Inject(method = {"onHitEntity", "m_5790_"}, at = @At("HEAD"), cancellable = true)
    private void bladeTetra$authorize(EntityHitResult hit, CallbackInfo ci) {
        if (!MikageNativeCombat.projectileContact((Entity) (Object) this, hit.getEntity())) ci.cancel();
    }
    @Inject(method = {"onHitEntity", "m_5790_"}, at = @At("RETURN"))
    private void bladeTetra$mobile(EntityHitResult hit, CallbackInfo ci) {
        if (MikageNativeCombat.authored((Entity) (Object) this) && hit.getEntity() instanceof LivingEntity living)
            StunManager.removeStun(living);
    }
    @Inject(method = "getPotionEffects", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$noPotion(CallbackInfoReturnable<List<MobEffectInstance>> ci) {
        if (MikageNativeCombat.authored((Entity) (Object) this)) ci.setReturnValue(List.of());
    }
}
