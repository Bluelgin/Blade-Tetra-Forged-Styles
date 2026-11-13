package dev.bladetetra.combat;

import dev.bladetetra.aura.SwordAuraMotionFilter;
import dev.bladetetra.challenge.MikageBladeAttackTrace;
import dev.bladetetra.easteregg.SakuraInfiniteDanceSA;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static dev.bladetetra.item.ModularSlashBladeItem.*;

@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class HabakiKnockback {
    private static final String REINFORCED_SLASH = "blade_tetra_reinforced_slash";
    // Resharped places this factor on victims before managed melee with a custom knockback.
    private static final String NATIVE_KNOCKBACK_FACTOR = "knockback_factor";
    private static final ThreadLocal<Hit> ACTIVE = new ThreadLocal<>();
    private record Hit(Entity target, boolean reinforced, boolean slash) {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSlashSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || slash.getIndirect() || slash.getKnockBack() != KnockBacks.cancel
                || !(slash.getShooter() instanceof LivingEntity user)) return;
        if (isReinforcedSlash(user)) slash.getPersistentData().putBoolean(REINFORCED_SLASH, true);
    }

    /** Only regular melee knife effects replace Resharped's momentum-clearing callback. */
    public static boolean replacesNativeCallback(EntitySlashEffect slash) {
        return slash.getKnockBack() == KnockBacks.cancel && slash.getPersistentData().getBoolean(REINFORCED_SLASH);
    }

    public static void melee(LivingEntity user, Entity target, Runnable attack) {
        Entity effect = MikageBladeAttackTrace.projectile();
        boolean reinforced = !target.getPersistentData().contains(NATIVE_KNOCKBACK_FACTOR)
                && (effect == null ? isReinforcedSlash(user)
                        : effect instanceof EntitySlashEffect slash && replacesNativeCallback(slash));
        Hit previous = ACTIVE.get();
        if (!reinforced && previous == null) {
            attack.run();
            return;
        }
        ACTIVE.set(new Hit(target, reinforced, effect != null));
        try {
            attack.run();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /** Scales the horizontal impulse after vanilla applies knockback resistance. */
    public static void applyImpulse(LivingEntity target, double x, double y, double z) {
        Hit hit = ACTIVE.get();
        if (hit != null && hit.target() == target && hit.reinforced()) {
            var before = target.getDeltaMovement();
            x = horizontal(before.x, x);
            z = horizontal(before.z, z);
            if (hit.slash()) y = before.y;
        }
        target.setDeltaMovement(x, y, z);
    }

    private static double horizontal(double incoming, double result) {
        double retained = incoming * .5D;
        return retained + (result - retained) * ComponentCombatValues.STRONG_KNOCKBACK_MULTIPLIER;
    }

    private static boolean isReinforcedSlash(LivingEntity user) {
        if (user.level().isClientSide()) return false;
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || !ComponentEffectResolver.hasModule(blade, HABAKI_SLOT, REINFORCED_HABAKI_MODULE)) return false;
        if (user instanceof ServerPlayer player && SakuraInfiniteDanceSA.active(player, blade)) return false;
        var state = blade.getCapability(BLADESTATE).orElse(null);
        if (state == null || CombatBalanceRuntime.isSlashArt(user, state)) return false;
        var combo = state.getComboSeq();
        return !DangakuStyleCombat.isSlash(combo) && SwordAuraMotionFilter.permits(combo, StyleResolver.resolve(blade));
    }

    private HabakiKnockback() {}
}
