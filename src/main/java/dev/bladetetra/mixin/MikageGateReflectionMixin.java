package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageGateSwordEntity;
import dev.bladetetra.challenge.MikageNativeCombat;
import mods.flammpfeil.slashblade.ability.ArrowReflector;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the native reflection algorithm. Only cast authorization and reflected-hit isolation are added. */
@Mixin(value = ArrowReflector.class, remap = false)
public abstract class MikageGateReflectionMixin {
    @Inject(method = "doReflect", at = @At("HEAD"), cancellable = true)
    private static void bladeTetra$authorize(Entity projectile, Entity actor, CallbackInfo ci) {
        if (projectile instanceof MikageGateSwordEntity sword && !sword.mayReflect(actor)) ci.cancel();
        else if (!MikageNativeCombat.mayReflect(projectile, actor)) ci.cancel();
    }
    @Inject(method = "doReflect", at = @At("RETURN"))
    private static void bladeTetra$reflected(Entity projectile, Entity actor, CallbackInfo ci) {
        if (projectile instanceof MikageGateSwordEntity sword) sword.nativeReflected(actor);
        else MikageNativeCombat.reflected(projectile);
    }
}
