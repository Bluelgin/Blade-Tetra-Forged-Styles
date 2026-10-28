package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.NativeBladeActionObserver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mods.flammpfeil.slashblade.util.AttackManager", remap = false)
public abstract class NativeBladeAttackObserverMixin {
    @Inject(method = "areaAttack(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;FZZZLjava/util/List;)Ljava/util/List;", at = @At("RETURN"))
    private static void bladeTetra$melee(LivingEntity user, Consumer<LivingEntity> before,
            float ratio, boolean force, boolean reset, boolean mute, List<Entity> exclude,
            CallbackInfoReturnable<List<Entity>> cir) {
        NativeBladeActionObserver.melee(user, ratio, exclude);
    }
    @Inject(method = "areaAttack(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Consumer;DZZFLjava/util/List;)Ljava/util/List;", at = @At("RETURN"))
    private static void bladeTetra$indirect(Entity owner, Consumer<LivingEntity> before,
            double reach, boolean force, boolean reset, float ratio, List<Entity> exclude,
            CallbackInfoReturnable<List<Entity>> cir) {
        NativeBladeActionObserver.indirect(owner, ratio);
    }
}
