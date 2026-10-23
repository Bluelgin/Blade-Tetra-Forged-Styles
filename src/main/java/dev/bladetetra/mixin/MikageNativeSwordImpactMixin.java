package dev.bladetetra.mixin;

import dev.bladetetra.challenge.*;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Native sword flight repeats a player-PvP filter after its ray. Only an authorized boss impact bypasses it. */
@Mixin(value = EntityAbstractSummonedSword.class, remap = false)
public abstract class MikageNativeSwordImpactMixin {
    @Group(name = "bladeTetra$impact", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 0)
    private boolean bladeTetra$namedImpact(TargetingConditions original, LivingEntity actor, LivingEntity target) {
        return bladeTetra$impact(original, actor, target);
    }
    @Group(name = "bladeTetra$impact", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;m_26885_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 0)
    private boolean bladeTetra$srgImpact(TargetingConditions original, LivingEntity actor, LivingEntity target) {
        return bladeTetra$impact(original, actor, target);
    }
    private boolean bladeTetra$impact(TargetingConditions original, LivingEntity actor, LivingEntity target) {
        Entity source = (Entity) (Object) this;
        if (!(actor instanceof MikageEntity)) return original.test(actor, target);
        return MikageNativeCombat.nativeImpact(source, target);
    }
}
