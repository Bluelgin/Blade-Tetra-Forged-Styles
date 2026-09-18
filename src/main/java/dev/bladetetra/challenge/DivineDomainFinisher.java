package dev.bladetetra.challenge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.UUID;

/** Final enemy phases create opportunities; only the players can supply the finishing damage. */
final class DivineDomainFinisher {
    private final MikageDivineSupportManager support;
    private UUID bossId;
    private int highestPhase;
    private long bindingEnd;
    private long nextBinding;
    private long nextHazard;
    private long hazardAt;
    private List<net.minecraft.world.phys.Vec3> hazardPoints = List.of();

    DivineDomainFinisher(MikageDivineSupportManager support) {
        this.support = support;
    }

    void bind(Mob boss) {
        bossId = boss.getUUID();
    }

    boolean binding(long now) {
        return now < bindingEnd;
    }

    boolean isBound(Entity entity, long now) {
        return entity.getUUID().equals(bossId) && binding(now);
    }

    void tick(List<Mob> enemies, long now) {
        if (bossId == null || !(support.level.getEntity(bossId) instanceof Mob boss)
                || !boss.isAlive()) {
            bindingEnd = 0;
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
        if (phase >= 2 && now >= nextHazard && !binding(now)) {
            nextHazard = now + 120;
            // A readable danger marker precedes the pulse, scoped to ritual participants.
            for (var player : support.players()) {
                support.effect("hazard", player.position(), player.getId(), 30, 0);
            }
            hazardAt = now + 25;
            hazardPoints = support.players().stream().map(Entity::position).toList();
        }
        if (hazardAt > 0 && now >= hazardAt) {
            hazardAt = 0;
            for (var player : support.players()) {
                if (hazardPoints.stream()
                        .anyMatch(point -> player.position().distanceToSqr(point) < 6.25)) {
                    player.hurt(support.level.damageSources().mobAttack(boss), 6);
                }
            }
            hazardPoints = List.of();
        }
        if (phase >= 3 && now >= nextBinding && support.available()) {
            bindingEnd = now + 100;
            nextBinding = now + 320;
            support.pose(3);
            support.effect("binding", boss.position(), boss.getId(), 100, 0);
            support.say("我只能替你压住它。这些亡魂认的是你的刀——斩断它。");
        }
        if (binding(now)) {
            boss.getNavigation().stop();
            boss.setTarget(null);
            boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    5, 9, false, false));
        } else if (bindingEnd > 0) {
            bindingEnd = 0;
            support.say("它挣脱了。继续，我会再找机会。");
        }
    }
}
