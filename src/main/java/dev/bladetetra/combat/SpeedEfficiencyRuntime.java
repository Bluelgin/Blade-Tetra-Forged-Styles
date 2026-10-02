package dev.bladetetra.combat;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.event.Scheduler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.timers.TimerCallback;

/** Stable boundary: read vanilla's final attribute, not version-specific Tetra honing internals. */
public final class SpeedEfficiencyRuntime {
    private SpeedEfficiencyRuntime() { }

    public static boolean eligible(LivingEntity user) {
        if (user == null || !user.isAlive()) return false;
        ItemStack blade = user.getMainHandItem();
        return blade.getItem() instanceof ModularSlashBladeItem
                && blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .map(state -> !state.isBroken() && !state.isSealed()).orElse(false);
    }

    public static double reduction(LivingEntity user) {
        return reduction(user, SpeedEfficiencyRules.Timing.GROUND_DODGE);
    }

    private static double reduction(LivingEntity user, SpeedEfficiencyRules.Timing timing) {
        if (!eligible(user) || !GameplayConfig.ENABLE_SPEED_EFFICIENCY.get()) return 0;
        // Not every blade-wielding mob exposes the player's attack-speed attribute.
        var speed = user.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return 0;
        double maximum = switch (timing) {
            case GROUND_DODGE -> GameplayConfig.SPEED_EFFICIENCY_MAX_REDUCTION.get();
            case SPECIAL_SWORDS -> GameplayConfig.SPEED_EFFICIENCY_SWORD_REDUCTION.get();
            case NORMAL_SA -> GameplayConfig.SPEED_EFFICIENCY_SA_REDUCTION.get();
            case SUPER_SA -> GameplayConfig.SPEED_EFFICIENCY_SUPER_REDUCTION.get();
        };
        return SpeedEfficiencyRules.reduction(speed.getValue(),
                GameplayConfig.SPEED_EFFICIENCY_REFERENCE.get(), timing, maximum);
    }

    public static int normalSaTicks(ISlashBladeState queriedState, LivingEntity user, int original) {
        if (!eligible(user) || user.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state != queriedState).orElse(true)) return original;
        return SpeedEfficiencyRules.ticks(original, reduction(user, SpeedEfficiencyRules.Timing.NORMAL_SA));
    }

    public static long scheduledAt(LivingEntity user, String name, long originalDue, long pressedAt) {
        long delay = originalDue - pressedAt;
        var timing = "chargeSuperSA".equals(name) || "sendPartical".equals(name)
                ? SpeedEfficiencyRules.Timing.SUPER_SA : SpeedEfficiencyRules.Timing.SPECIAL_SWORDS;
        return pressedAt + SpeedEfficiencyRules.scheduledDelay(name, delay, reduction(user, timing));
    }

    /** One native callback, same input timestamp; accelerated preparation cannot be transferred to another blade/world. */
    public static void schedule(Scheduler scheduler, String name, long due,
            TimerCallback<LivingEntity> callback, LivingEntity user, long pressedAt) {
        long adjusted = scheduledAt(user, name, due, pressedAt);
        if (adjusted == due) {
            scheduler.schedule(name, due, callback);
            return;
        }
        ItemStack sourceBlade = user.getMainHandItem();
        var sourceLevel = user.level();
        scheduler.schedule(name, adjusted, (actor, queue, now) -> {
            if (actor == user && actor.level() == sourceLevel
                    && actor.getMainHandItem() == sourceBlade && eligible(actor)) {
                callback.handle(actor, queue, now);
            }
        });
    }
}
