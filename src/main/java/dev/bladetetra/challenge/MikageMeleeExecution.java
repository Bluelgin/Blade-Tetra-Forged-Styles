package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Stable native sword phrases: clash early blades, parry the finisher or escape the locked lane. */
final class MikageMeleeExecution implements SkillExecution {
    private final MikageEntity owner;
    private final MikageMove move;
    private final UUID target;
    private final Runnable committed;
    private final MeleeRhythm rhythm;
    private final MikageMeleeFootwork footwork;
    private CastScope scope;
    private boolean clearedHit;
    MikageMeleeExecution(MikageEntity owner, ServerPlayer target, MikageMove move, Runnable committed) {
        this.owner = owner; this.move = move; this.target = target.getUUID(); this.committed = committed;
        rhythm = new MeleeRhythm(move); footwork = new MikageMeleeFootwork(owner, move);
    }
    @Override public void start(CastScope scope) {
        this.scope = scope; owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        owner.setAction(MikageEntity.MikageAction.IAIDO_READY, move.windup);
        sound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.3F);
    }
    @Override public boolean windingUp() { return rhythm.windingUp(); }
    @Override public Status tick() {
        if (!(((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer player)
                || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        boolean advanced = rhythm.tick();
        if (rhythm.hit() && !clearedHit) {
            clearedHit = true; MikageNativeCombat.clearAttacks(scope);
            owner.setAction(MikageEntity.MikageAction.CAST_READY, MeleeRhythm.HIT_RECOVERY);
        }
        if (advanced) footwork.tick(player, rhythm);
        if (move == MikageMove.ZANSHIN && rhythm.windingUp()) MikageNativeAttacks.tell(owner, footwork.aim(), MikageMove.CUT);
        if (advanced && rhythm.releaseDue()) {
            int beat = rhythm.release();
            if (beat == 0) committed.run();
            MikageNativeCombat.clearAttacks(scope);
            boolean heavy = move == MikageMove.CLEAVE && beat == move.releases().length - 1;
            owner.setAction(heavy ? MikageEntity.MikageAction.HEAVY_CLEAVE : MikageEntity.MikageAction.COMBO_SLASH, 10);
            MikageNativeCombat.run(owner, scope, rule(beat), () -> MikageNativeAttacks.release(owner, scope,
                    move, player.position().add(0, .8, 0), footwork.heading(), move.releases()[beat]));
            footwork.released(player); sound(SoundEvents.PLAYER_ATTACK_SWEEP, heavy ? .6F : 1.1F);
        }
        return rhythm.countered() ? Status.COUNTERED : rhythm.complete() ? Status.COMPLETE : Status.RUNNING;
    }
    private MikageNativeCombat.Rule rule(int beat) {
        return new MikageNativeCombat.Rule(target, true, true, (player, source) -> {
            if (!rhythm.mayContact(beat) || source.tickCount > MeleeRhythm.CONTACT_TICKS) return false;
            if (owner.duel().parry(player, owner.getEyePosition())) {
                rhythm.parry(beat);
                if (rhythm.countered() && !scope.closed()) owner.duel().rewardOpening(MeleeRhythm.FINISHER_OPENING);
                return false;
            }
            return true;
        }, (player, source) -> rhythm.hit(beat));
    }
    private void sound(SoundEvent sound, float pitch) {
        owner.level().playSound(null, owner.blockPosition(), sound, SoundSource.HOSTILE, .8F, pitch);
    }
    @Override public void stop(StopReason reason) {
        if (rhythm.beat() >= 0 && (reason == StopReason.COMPLETE || reason == StopReason.COUNTERED))
            owner.movement().meleeExchangeCompleted(target);
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.setDeltaMovement(Vec3.ZERO); owner.fallDistance = 0;
        owner.combatDirector().techniqueCooldown = Math.max(12, owner.combatDirector().techniqueCooldown);
    }
}
