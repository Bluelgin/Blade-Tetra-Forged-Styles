package dev.bladetetra.mixin;

import dev.bladetetra.combat.HabakiKnockback;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(value = EntitySlashEffect.class, remap = false)
public abstract class HabakiNativeSlashMixin {
    @ModifyArg(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
            target = "Lmods/flammpfeil/slashblade/util/AttackManager;areaAttack(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;FZZZLjava/util/List;)Ljava/util/List;"), index = 1)
    private Consumer<LivingEntity> bladeTetra$keepMomentum(Consumer<LivingEntity> callback) {
        return HabakiKnockback.replacesNativeCallback((EntitySlashEffect) (Object) this) ? target -> {} : callback;
    }
}
