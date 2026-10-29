package dev.bladetetra.kyouka;

import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.function.Consumer;

/** Observes the actual native managed hit. The callback always runs once, with exception-safe scope. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class NativeBladeActionObserver {
    private static final ThreadLocal<Window> WINDOW = new ThreadLocal<>();
    private static final class Window {
        final LivingEntity target;
        final float health;
        ServerPlayer owner;
        DamageSource source;
        float baseline;
        Window(LivingEntity target) { this.target = target; health = target.getHealth(); }
    }
    public static void committed(LivingEntity user, ISlashBladeState state) {
        if (user instanceof ServerPlayer p && KyoukaMirrorSessions.eligible(p.getMainHandItem()))
            KyoukaMirrorSessions.committed(p, state);
    }
    public static Consumer<Entity> observe(Consumer<Entity> nativeAttack) {
        return target -> {
            if (!(target instanceof LivingEntity living) || target.level().isClientSide()) {
                nativeAttack.accept(target); return;
            }
            Window previous = WINDOW.get(), current = new Window(living);
            WINDOW.set(current);
            boolean completed = false;
            try { nativeAttack.accept(target); completed = true; }
            finally {
                if (previous == null) WINDOW.remove(); else WINDOW.set(previous);
                if (completed && current.owner != null && current.baseline > 0
                        && living.getHealth() < current.health)
                    KyoukaMirrorSessions.hit(current.owner, living, current.baseline, current.source);
            }
        };
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent e) {
        Window w = WINDOW.get();
        var source = e.getSource();
        if (w == null || w.target != e.getEntity() || w.owner != null
                || InheritedCombatDamage.isInherited(source) || SoulLegacyDamageGuard.isSecondary(source)
                || !(source.getEntity() instanceof ServerPlayer p)
                || !KyoukaMirrorSessions.eligible(p.getMainHandItem())
                || !KyoukaMirrorSessions.legal(p, w.target)) return;
        Entity direct = source.getDirectEntity();
        if (direct != p && !(direct instanceof EntitySlashEffect slash && slash.getShooter() == p)) return;
        if (!Float.isFinite(e.getAmount()) || e.getAmount() <= 0 || e.getAmount() > 1024) return;
        w.owner = p; w.source = source; w.baseline = e.getAmount();
    }
    private NativeBladeActionObserver() {}
}
