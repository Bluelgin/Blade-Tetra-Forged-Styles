package dev.bladetetra.mixin;

import dev.bladetetra.challenge.MikageNativeCombat;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Apply encounter authorization before native forceHit, stun and knockback callbacks. */
@Mixin(targets = "mods.flammpfeil.slashblade.util.TargetSelector", remap = false)
public abstract class MikageNativeTargetsMixin {
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;D)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private static void bladeTetra$melee(Level level, LivingEntity actor, AABB bounds, double reach,
            CallbackInfoReturnable<List<Entity>> ci) {
        var result = MikageNativeCombat.targets(actor, bounds, reach);
        if (result != null) ci.setReturnValue(result);
    }
    @Inject(method = "getTargettableEntitiesWithinAABB(Lnet/minecraft/world/level/Level;DLnet/minecraft/world/entity/Entity;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private static void bladeTetra$projectile(Level level, double reach, Entity source,
            CallbackInfoReturnable<List<Entity>> ci) {
        var result = MikageNativeCombat.targets(source, source.getBoundingBox().inflate(reach), 0);
        if (result != null) ci.setReturnValue(result);
    }
}
