package dev.bladetetra.compat.attachments;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.effect.ItemEffectHandler;

/** Uses the same hit-effect dispatcher as Tetra swords, without another probability/damage table. */
public final class NativeSwordHitEffects {
    public static void apply(ItemStack blade, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide()) {
            ItemEffectHandler.applyHitEffects(blade, target, attacker);
        }
    }

    private NativeSwordHitEffects() {}
}
