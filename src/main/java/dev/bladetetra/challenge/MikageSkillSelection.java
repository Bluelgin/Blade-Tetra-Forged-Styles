package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.SkillExecution;
import dev.bladetetra.challenge.mikage.SkillSpec;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** New authored skills and the explicit old pool meet here, outside the entity and executor. */
final class MikageSkillSelection {
    private static final SkillSpec GATES = new SkillSpec("blade_tetra:thousand_gates", "mirror",
            Set.of(SkillSpec.Tactic.GAP_CLOSE, SkillSpec.Tactic.COUNTER, SkillSpec.Tactic.ANTI_AIR), 0, 32);
    private static final SkillSpec CORRIDOR = new SkillSpec("blade_tetra:gate_corridor", "mirror",
            Set.of(SkillSpec.Tactic.GAP_CLOSE, SkillSpec.Tactic.ANTI_AIR, SkillSpec.Tactic.GUARD_PRESSURE), 0, 48);
    private static final SkillSpec BARRAGE = new SkillSpec("blade_tetra:gate_barrage", "mirror",
            Set.of(SkillSpec.Tactic.RANGED, SkillSpec.Tactic.GUARD_PRESSURE, SkillSpec.Tactic.SETUP), 0, 48);
    private int barrageReadyAt;
    private int corridorReadyAt;
    private final MikageEntity owner;
    private int gatesReadyAt;

    MikageSkillSelection(MikageEntity owner) { this.owner = owner; }
    List<SkillSpec> available(ServerPlayer target, int phase) {
        if (owner.movement().needsMeleeExchange(target))
            return List.of(MikageLegacySkillPool.spec(MikageEntity.Technique.BLADE_COMBO));
        var choices = new ArrayList<>(MikageLegacySkillPool.available(owner, target, phase));
        if (owner.tickCount >= gatesReadyAt && owner.distanceToSqr(target) <= 32 * 32)
            choices.add(GATES);
        if (owner.tickCount >= corridorReadyAt && owner.distanceToSqr(target) <= 48 * 48)
            choices.add(CORRIDOR);
        if (owner.tickCount >= barrageReadyAt && target.onGround() && owner.distanceToSqr(target) <= 48 * 48)
            choices.add(BARRAGE);
        return choices;
    }
    SkillSpec spec(String id) {
        if (BARRAGE.id().equals(id)) return BARRAGE;
        if (CORRIDOR.id().equals(id)) return CORRIDOR;
        return GATES.id().equals(id) ? GATES : MikageLegacySkillPool.spec(MikageLegacySkillPool.technique(id));
    }
    SkillExecution create(String id, ServerPlayer target, Runnable committed) {
        if (GATES.id().equals(id)) {
            return new MikageThousandGatesExecution(owner, target, () -> {
                gatesReadyAt = owner.tickCount + 400;
                committed.run();
            });
        }
        if (CORRIDOR.id().equals(id)) return new MikageGateCorridorExecution(owner, target, () -> {
            corridorReadyAt = owner.tickCount + 480; committed.run();
        });
        if (BARRAGE.id().equals(id)) return new MikageGateBarrageExecution(owner, target, () -> {
            barrageReadyAt = owner.tickCount + 720; committed.run();
        });
        owner.legacyEffects().lockBladeTarget(target);
        return new MikageLegacySkillExecution(owner, MikageLegacySkillPool.technique(id), target, committed);
    }
}
