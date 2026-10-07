package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CastScope;
import dev.bladetetra.challenge.mikage.SkillExecution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/** Transitional release instance. Existing effects remain in the legacy adapter. */
final class MikageLegacySkillExecution implements SkillExecution {
    private final MikageEntity owner;
    private final MikageEntity.Technique technique;
    private final UUID target;
    private final Runnable reaction;
    private final Runnable committed;
    private int windup;
    private boolean released;
    private CastScope scope;

    MikageLegacySkillExecution(MikageEntity owner, MikageEntity.Technique technique,
            ServerPlayer target, Runnable committed) {
        this.owner = owner;
        this.technique = technique;
        this.target = target.getUUID();
        this.committed = committed;
        this.reaction = null;
        windup = switch (technique.style) {
            case IAIDO -> 16;
            case RENGEKI -> 8;
            case DANGAKU -> 14;
            default -> 9;
        };
    }

    MikageLegacySkillExecution(MikageEntity owner, ServerPlayer target, Runnable reaction) {
        this.owner = owner;
        this.target = target.getUUID();
        this.reaction = reaction;
        this.committed = () -> {};
        this.technique = MikageEntity.Technique.NONE;
    }

    @Override public boolean windingUp() { return !released; }
    void withScope(Runnable task) { owner.attackTimeline().inScope(scope, task); }

    @Override public void start(CastScope scope) {
        this.scope = scope;
        scope.own(() -> owner.attackTimeline().cancel(scope));
        if (reaction != null) {
            withScope(reaction);
            released = true;
            return;
        }
        owner.getNavigation().stop();
        owner.setAction(switch (technique.style) {
            case IAIDO -> MikageEntity.MikageAction.IAIDO_READY;
            case RENGEKI -> MikageEntity.MikageAction.COMBO_READY;
            case DANGAKU -> MikageEntity.MikageAction.HEAVY_READY;
            default -> MikageEntity.MikageAction.CAST_READY;
        }, windup);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER,
                SoundSource.HOSTILE, 0.48F,
                technique.style == MikageEntity.SwordDiscipline.IAIDO ? 1.45F : 1.1F);
    }

    @Override public Status tick() {
        if (!(owner.level() instanceof ServerLevel server)) return Status.TARGET_LOST;
        var tracked = server.getEntity(target);
        if (!(tracked instanceof ServerPlayer player) || !owner.encounter().eligible(player)) {
            return Status.TARGET_LOST;
        }
        if (!released) {
            owner.getNavigation().stop();
            owner.legacyEffects().lockBladeTarget(player);
            owner.lookAt(player, 180, 180);
            if (technique == MikageEntity.Technique.STEP_IAIDO && windup == 4) {
                owner.legacyEffects().stepIaido(player, server);
            }
            if (--windup <= 0) {
                released = true;
                owner.setAction(MikageLegacySkillEffects.actionForStrike(technique),
                        MikageLegacySkillEffects.actionLength(technique));
                withScope(() -> owner.legacyEffects().performTechnique(technique, player, server));
                committed.run();
                owner.advanceBoundaryFlashCharge(technique, server);
            }
            return Status.RUNNING;
        }
        return owner.legacySkillBusy() || owner.actionRemainingTicks() > 0
                || owner.attackTimeline().hasPending(scope) ? Status.RUNNING : Status.COMPLETE;
    }

    @Override public void stop(StopReason reason) {
        if (reason != StopReason.COMPLETE) {
            MikageEncounterCleanup.foreground(owner);
            owner.setAction(MikageEntity.MikageAction.IDLE, 1);
            owner.combatDirector().techniqueCooldown = Math.max(20, owner.combatDirector().techniqueCooldown);
        }
    }
}
