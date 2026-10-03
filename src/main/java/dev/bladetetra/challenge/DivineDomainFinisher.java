package dev.bladetetra.challenge;

import net.minecraft.world.entity.Mob;

import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;

/** Final enemy phases create opportunities; only the players can supply the finishing damage. */
final class DivineDomainFinisher {
    private final MikageDivineSupportManager support;
    private UUID bossId;
    private int highestPhase;
    private long nextHazard;
    private long hazardAt;
    private List<net.minecraft.world.phys.Vec3> hazardPoints = List.of();

    DivineDomainFinisher(MikageDivineSupportManager support) {
        this.support = support;
    }

    void bind(Mob boss) {
        bossId = boss.getUUID();
    }

    void tick(long now) {
        if (bossId == null || !(support.level.getEntity(bossId) instanceof Mob boss)
                || !boss.isAlive()) {
            return;
        }
        int phase = DivineSupportRules.phase(boss.getHealth(), boss.getMaxHealth());
        if (phase > highestPhase) {
            int firstCrossedPhase = highestPhase + 1;
            highestPhase = phase;
            int count = support.session.tier == DivineDomainTier.AVICI ? 4 : 2;
            // A large hit may cross several thresholds at once. Process every crossed
            // phase so burst damage cannot silently skip the intended reinforcements.
            for (int crossed = firstCrossedPhase; crossed <= phase; crossed++) {
                for (int i = 0; i < count; i++) {
                    support.session.spawnOne(support.level, support.session.wave,
                            70 + crossed * 7 + i, false);
                }
            }
            support.say(phase >= 2
                    ? "神域正在失控。留意脚下。"
                    : "它在呼唤更多亡魂。");
        }
        if (phase >= 2 && now >= nextHazard) {
            nextHazard = now + 120;
            // A readable danger marker precedes the pulse, scoped to ritual participants.
            for (var player : support.players()) {
                support.effect("hazard", player.position(), player.getId(), 30, 0);
            }
            hazardAt = now + 25;
            hazardPoints = support.players().stream().map(net.minecraft.world.entity.Entity::position).toList();
        }
        if (hazardAt > 0 && now >= hazardAt) {
            hazardAt = 0;
            for (var player : support.players()) {
                if (hazardPoints.stream()
                        .anyMatch(point -> player.position().distanceToSqr(point) < 6.25)) {
                    // A ground pulse is not a direct melee hit: the fire ring must not cancel it.
                    player.hurt(new DamageSource(support.level.damageSources().mobAttack(boss).typeHolder(), null, boss), 6);
                }
            }
            hazardPoints = List.of();
        }
    }
}
