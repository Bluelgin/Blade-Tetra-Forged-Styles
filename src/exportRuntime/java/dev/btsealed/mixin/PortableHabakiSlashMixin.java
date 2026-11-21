package dev.btsealed.mixin;

import dev.btsealed.runtime.PortableHabakiKnockback;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = EntitySlashEffect.class, remap = false)
public abstract class PortableHabakiSlashMixin {
    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"))
    private void btSealed$startSlashTick(CallbackInfo ci) {
        PortableHabakiKnockback.beginSlashTick((EntitySlashEffect) (Object) this);
    }

    @Inject(method = {"tick", "m_8119_"}, at = @At("RETURN"))
    private void btSealed$finishSlashTick(CallbackInfo ci) {
        PortableHabakiKnockback.endSlashTick();
    }

    @ModifyArg(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/util/AttackManager;areaAttack(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;FZZZLjava/util/List;)Ljava/util/List;"), index = 1)
    private Consumer<LivingEntity> btSealed$keepMomentum(Consumer<LivingEntity> callback) {
        return PortableHabakiKnockback.keepMomentum((EntitySlashEffect) (Object) this)
                ? target -> {} : callback;
    }
}
