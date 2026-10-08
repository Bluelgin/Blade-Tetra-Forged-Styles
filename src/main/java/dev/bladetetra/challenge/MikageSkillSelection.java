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
    private final MikageEntity owner;
    private int gatesReadyAt;

    MikageSkillSelection(MikageEntity owner) { this.owner = owner; }
    List<SkillSpec> available(ServerPlayer target, int phase) {
        var choices = new ArrayList<>(MikageLegacySkillPool.available(owner, target, phase));
        if (owner.tickCount >= gatesReadyAt && owner.distanceToSqr(target) <= 32 * 32)
            choices.add(GATES);
        return choices;
    }
    SkillSpec spec(String id) {
        return GATES.id().equals(id) ? GATES : MikageLegacySkillPool.spec(MikageLegacySkillPool.technique(id));
    }
    SkillExecution create(String id, ServerPlayer target, Runnable committed) {
        if (GATES.id().equals(id)) {
            return new MikageThousandGatesExecution(owner, target, () -> {
                gatesReadyAt = owner.tickCount + 400;
                committed.run();
            });
        }
        owner.legacyEffects().lockBladeTarget(target);
        return new MikageLegacySkillExecution(owner, MikageLegacySkillPool.technique(id), target, committed);
    }
}
