package dev.bladetetra.mixin;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import se.mickelus.tetra.effect.ItemEffectHandler;

@Mixin(value = ItemEffectHandler.class, remap = false)
abstract class ForgingBleedingChanceMixin {
    @ModifyConstant(method = "applyHitEffects", constant = @Constant(floatValue = 0.30f))
    private static float bladeTetra$scaleCoreBleeding(float chance, ItemStack stack,
            LivingEntity target, LivingEntity attacker) {
        return stack.getItem() instanceof ModularSlashBladeItem blade
                ? blade.getForgingBleedingChance(stack, chance)
                : chance;
    }
}
