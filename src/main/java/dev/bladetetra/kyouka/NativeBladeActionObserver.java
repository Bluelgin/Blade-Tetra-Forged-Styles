package dev.bladetetra.kyouka;

import dev.bladetetra.combat.*;
import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Read-only committed motion and completed attack-window observations. Never invokes a combo callback. */
@Mod.EventBusSubscriber(modid = "blade_tetra")
public final class NativeBladeActionObserver {
    public static void committed(LivingEntity user, ISlashBladeState state) {
        if (!(user instanceof ServerPlayer player)) return;
        var s = KyoukaMirrorSessions.SESSIONS.get(player.getUUID());
        if (s == null || !KyoukaMirrorSessions.valid(s)) return;
        s.combo = state.getComboSeq(); s.started = state.getLastActionTime();
        s.yaw = user.getYRot(); s.action++;
        if (s.budgets.size() >= 32) s.budgets.remove(s.budgets.keySet().iterator().next());
        s.budgets.put(s.action, new KyoukaMirrorSession.Budget());
        KyoukaMirrorSessions.changed(s);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || event.getEntity().getClass() != EntitySlashEffect.class
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof ServerPlayer player)) return;
        var s = KyoukaMirrorSessions.SESSIONS.get(player.getUUID());
        if (s != null && s.action > 0 && s.shots.size() < 64 && KyoukaMirrorSessions.valid(s)
                && !InheritedCombatDamage.isInherited(player.damageSources().playerAttack(player)))
            s.shots.put(slash.getAlreadyHits(), new KyoukaMirrorSession.Shot(slash, s.action, new HashSet<>()));
    }
    public static void melee(LivingEntity user, float ratio, List<Entity> exclude) {
        if (!(user instanceof ServerPlayer player)) return;
        var s = KyoukaMirrorSessions.SESSIONS.get(player.getUUID());
        if (s == null || !KyoukaMirrorSessions.valid(s) || !Float.isFinite(ratio) || ratio <= 0 || ratio > 16) return;
        var shot = exclude == null ? null : s.shots.get(exclude);
        // Unknown external exclusion lists have no trustworthy provenance.
        if (exclude != null && shot == null) return;
        if (shot != null && (shot.entity().getIndirect() || !shot.entity().isAlive()
                || player.serverLevel().getEntity(shot.entity().getId()) != shot.entity())) return;
        long action = shot == null ? s.action : shot.action();
        String key = shot == null ? "direct/" + player.level().getGameTime() + "/" + Float.floatToIntBits(ratio)
                : "slash/" + shot.entity().getUUID() + "/" + (shot.entity().doCycleHit() ? shot.entity().tickCount : 0);
        // The actual native helper preserves target enchantments, rank, refine, critical and ratio.
        double baseline = AttackHelper.calculateTotalDamage(player, s.target, ratio,
                AttackHelper.isCriticalHit(player, s.target));
        accept(s, action, key, baseline, player.damageSources().playerAttack(player),
                shot == null ? null : shot.entity());
    }
    public static void indirect(Entity entity, float ratio) {
        if (entity.getClass() != EntitySlashEffect.class || !(entity instanceof EntitySlashEffect slash)
                || !slash.getIndirect() || !(slash.getShooter() instanceof ServerPlayer player)) return;
        var s = KyoukaMirrorSessions.SESSIONS.get(player.getUUID());
        if (s == null || !KyoukaMirrorSessions.valid(s)) return;
        var shot = s.shots.get(slash.getAlreadyHits());
        if (shot == null || !slash.isAlive() || player.serverLevel().getEntity(slash.getId()) != slash) return;
        double baseline = (slash.getDamage() * player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + AttackHelper.getRankBonus(player)) * ratio * AttackManager.getSlashBladeDamageScale(player)
                * SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
        accept(s, shot.action(), "slash/" + slash.getUUID() + "/" + (slash.doCycleHit() ? slash.tickCount : 0),
                baseline, player.damageSources().indirectMagic(slash, player), slash);
    }
    private static void accept(KyoukaMirrorSession s, long action, String key, double baseline,
            DamageSource source, EntitySlashEffect slash) {
        if (InheritedCombatDamage.isInherited(source)
                || dev.bladetetra.easteregg.SoulLegacyDamageGuard.isSecondary(source)) return;
        var budget = s.budgets.get(action);
        if (budget == null || budget.windows >= KyoukaRules.MAX_WINDOWS || budget.keys.contains(key)
                || KyoukaRules.budget(baseline) <= 0) return;
        budget.keys.add(key); budget.windows++;
        if (!budget.paid) {
            if (!KyoukaMirrorSessions.spend(s, GameplayConfig.KYOUKA_ACTION_COST.get())) {
                KyoukaMirrorSessions.stop(s); return;
            }
            budget.paid = true;
        }
        if (!KyoukaMirrorSessions.valid(s)) { KyoukaMirrorSessions.stop(s); return; }
        KyoukaMirrorDamageService.enqueue(s, source, KyoukaRules.budget(baseline),
                CombatBalanceRuntime.kind(s.owner, slash));
    }
    private NativeBladeActionObserver() {}
}
