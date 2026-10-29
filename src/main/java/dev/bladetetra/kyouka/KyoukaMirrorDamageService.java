package dev.bladetetra.kyouka;

import dev.bladetetra.combat.*;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** One extra hit shared by all three visual mirrors, or one delayed water impact. */
public final class KyoukaMirrorDamageService {
    static void apply(KyoukaMirrorSessions.State s, KyoukaMirrorSessions.Pending h) {
        var l = h.slash(); var p = s.owner;
        var entity = p.serverLevel().getEntity(l.target());
        if (!(entity instanceof LivingEntity target) || !KyoukaMirrorSessions.legal(p, target)
                || !KyoukaMirrorSessions.inRange(p, target) || target.position().distanceToSqr(l.anchor()) > 9
                || !p.hasLineOfSight(target) || target.skipAttackInteraction(p)
                || MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p, target))) return;
        float amount = h.water() ? l.baseline()*.40F : KyoukaRules.budget(l.baseline());
        int immunity = target.invulnerableTime;
        try {
            target.invulnerableTime = 0;
            boolean hit = SoulLegacyDamageGuard.apply(() -> CombatBalanceRuntime.authoredDamage(target,
                    dev.bladetetra.easteregg.KyoukaAwakening.mirrorDamage(p.serverLevel(), p), amount, l.style(), l.kind()));
            if (hit) WaterMirrorCombat.visual(p, target, dev.bladetetra.network.KyoukaVfxPacket.WATER_IMPACT, 12);
        } finally { target.invulnerableTime = immunity; }
    }
    private KyoukaMirrorDamageService() {}
}
