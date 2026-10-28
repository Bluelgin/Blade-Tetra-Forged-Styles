package dev.bladetetra.kyouka;

import dev.bladetetra.combat.*;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import java.util.*;

/** A single extra hit per legitimate native window: never one hit per rendered blade. */
public final class KyoukaMirrorDamageService {
    private record Hit(KyoukaMirrorSession session, DamageSource source, float amount,
            CombatBalanceRules.AttackKind kind, BladeStyle style) {}
    private static final List<Hit> PENDING = new ArrayList<>();
    static void enqueue(KyoukaMirrorSession s, DamageSource source, float amount,
            CombatBalanceRules.AttackKind kind) {
        if (PENDING.size() < 4096) PENDING.add(new Hit(s, source, amount, kind, StyleResolver.resolve(s.blade)));
    }
    static void flush() {
        var hits = List.copyOf(PENDING); PENDING.clear();
        for (var h : hits) {
            var s = h.session;
            if (KyoukaMirrorSessions.SESSIONS.get(s.owner.getUUID()) != s || !KyoukaMirrorSessions.valid(s)
                    || !s.owner.hasLineOfSight(s.target)) continue;
            // Both player attack protection and the ordinary LivingAttack/Hurt/Damage pipeline remain active.
            // ForgeHooks.onPlayerAttackTarget also invokes ItemSlashBlade.onLeftClickEntity,
            // which synthesizes L_CLICK/progressCombo unless native onClick is set. Post only
            // the protection event here: a mirror impact must never masquerade as player input.
            if (MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(s.owner, s.target))
                    || s.target.skipAttackInteraction(s.owner)) continue;
            double balanced = h.amount * dev.bladetetra.config.CombatBalanceConfig.multiplier(h.style, h.kind);
            if (!Double.isFinite(balanced) || balanced <= 0 || balanced > 768) continue;
            int immunity = s.target.invulnerableTime;
            var motion = s.target.getDeltaMovement();
            boolean marked = s.target.hurtMarked;
            try {
                s.target.invulnerableTime = 0;
                SoulLegacyDamageGuard.apply(() -> CombatBalanceRuntime.authoredDamage(
                        s.target, dev.bladetetra.easteregg.KyoukaAwakening.mirrorDamage(s.owner.serverLevel(), s.owner),
                        h.amount, h.style, h.kind));
            } finally {
                // Only this native-authorized window bypasses hurt cooldown. No permanent immunity stripping or knockback.
                s.target.invulnerableTime = immunity;
                s.target.setDeltaMovement(motion); s.target.hurtMarked = marked;
            }
        }
    }
    static void clear() { PENDING.clear(); }
    private KyoukaMirrorDamageService() {}
}
