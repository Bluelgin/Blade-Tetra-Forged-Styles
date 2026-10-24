package dev.bladetetra.aura;

import dev.bladetetra.BladeTetra;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppress only the current aura contact's knockback; never freeze or reset target velocity. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class SwordAuraImpact {
    private static final ThreadLocal<Entity> TARGET = new ThreadLocal<>();

    static boolean hurt(Entity target, DamageSource source, float amount) {
        Entity previous = TARGET.get();
        TARGET.set(target);
        try {
            return target.hurt(source, amount);
        } finally {
            if (previous == null) TARGET.remove();
            else TARGET.set(previous);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventAuraKnockback(LivingKnockBackEvent event) {
        if (TARGET.get() == event.getEntity()) event.setCanceled(true);
    }

    private SwordAuraImpact() { }
}
