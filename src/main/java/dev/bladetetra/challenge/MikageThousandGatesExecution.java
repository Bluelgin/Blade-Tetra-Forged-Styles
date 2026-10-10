package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CastScope;
import dev.bladetetra.challenge.mikage.SkillExecution;
import dev.bladetetra.challenge.mikage.ThousandGatesSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** One target, committed spatial cues, five consecutive parries; no legacy global tick state. */
final class MikageThousandGatesExecution implements SkillExecution {
    private final MikageEntity owner;
    private final UUID target;
    private final ThousandGatesSequence sequence = new ThousandGatesSequence();
    private final MikageThousandGatesPresentation visuals;
    private final Runnable committed;
    private CastScope scope;
    private Vec3 arrival, direction;
    private boolean oldGravity, oldPhysics, oldSilent, oldInvisible, restored;
    private int hitRecovery, strikeWindow;

    MikageThousandGatesExecution(MikageEntity owner, ServerPlayer player, Runnable committed) {
        this.owner = owner; target = player.getUUID();
        this.committed = committed;
        visuals = new MikageThousandGatesPresentation(owner);
    }
    @Override public void start(CastScope scope) {
        this.scope = scope;
        oldGravity = owner.isNoGravity(); oldPhysics = owner.noPhysics;
        oldSilent = owner.isSilent(); oldInvisible = owner.isInvisible();
        scope.own(this::restore);
        owner.setNoGravity(true);
        owner.setAction(MikageEntity.MikageAction.CAST_READY, 12);
        visuals.portal("enter", scope.id(), 12);
        if (((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer player)
            player.displayClientMessage(Component.translatable("message.blade_tetra.mikage.thousand_gates",
                    0, ThousandGatesSequence.REQUIRED_PARRIES), true);
        committed.run();
    }
    @Override public boolean windingUp() { return !sequence.terminal(); }

    @Override public Status tick() {
        if (!(owner.level() instanceof ServerLevel level)
                || !(level.getEntity(target) instanceof ServerPlayer player)
                || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.setTarget(player); owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        if (sequence.stage() == ThousandGatesSequence.Stage.BROKEN) {
            owner.duel().breakPursuitBalance(); visuals.portal("break", scope.id(), 24); return Status.COUNTERED;
        }
        if (strikeWindow > 0) {
            if (--strikeWindow == 0 && sequence.stage() == ThousandGatesSequence.Stage.REVEAL) {
                MikageNativeCombat.clearAttacks(scope); sequence.contact(ThousandGatesSequence.Contact.MISS);
                progress(player); visuals.portal("enter", scope.id(), 6);
            }
            return Status.RUNNING;
        }
        if (sequence.stage() == ThousandGatesSequence.Stage.HIT)
            return --hitRecovery <= 0 ? Status.COMPLETE : Status.RUNNING;
        if (sequence.stage() == ThousandGatesSequence.Stage.REVEAL) face();
        if (!sequence.tick()) return Status.RUNNING;
        switch (sequence.stage()) {
            case HIDDEN -> conceal(true);
            case CUE -> {
                Vec3 next = MikageGateArrival.choose(owner, player, arrival);
                if (next == null) { sequence.retryArrival(); break; }
                arrival = next;
                visuals.cue(player, arrival, sequence.streak());
            }
            case REVEAL -> {
                if (sequence.remaining() == 0) {
                    strike(level, player);
                } else {
                    // Terrain may change after the cue. Never announce one place and attack from another.
                    if (!MikageGateArrival.safe(owner, player, level, arrival)) {
                        sequence.retryArrival();
                        break;
                    }
                    owner.movement().teleportWithinArena(arrival.x, arrival.y, arrival.z);
                    direction = player.position().subtract(arrival).multiply(1, 0, 1).normalize();
                    if (direction.lengthSqr() < .001) direction = new Vec3(0, 0, 1);
                    conceal(false); face();
                    owner.setAction(MikageEntity.MikageAction.IAIDO_READY, sequence.remaining());
                    visuals.portal("exit", scope.id(), sequence.remaining());
                    visuals.drawSound(player, arrival, sequence.streak());
                }
            }
            default -> { }
        }
        return Status.RUNNING;
    }

    private void strike(ServerLevel level, ServerPlayer player) {
        owner.setAction(MikageEntity.MikageAction.IAIDO_DRAW, 6); visuals.slash(scope.id()); strikeWindow = 6;
        var rule = new MikageNativeCombat.Rule(target, true, true, (p, source) -> {
            if (sequence.stage() != ThousandGatesSequence.Stage.REVEAL || sequence.remaining() != 0) return false;
            if (owner.duel().parryPursuit(p, arrival.add(0, .9, 0))) {
                sequence.contact(ThousandGatesSequence.Contact.PARRY); progress(p);
                MikageNativeCombat.clearAttacks(scope); strikeWindow = 0;
                if (!sequence.terminal()) visuals.portal("enter", scope.id(), 6);
                return false;
            }
            return true;
        }, (p, source) -> {
            if (sequence.stage() == ThousandGatesSequence.Stage.REVEAL && sequence.remaining() == 0) {
                sequence.contact(ThousandGatesSequence.Contact.HIT); hitRecovery = 6; strikeWindow = 0; progress(p);
            }
        });
        MikageNativeCombat.run(owner, scope, rule,
                () -> mods.flammpfeil.slashblade.util.AttackManager.doSlash(owner, -12, false, false, .65));
    }
    private void progress(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.blade_tetra.mikage.thousand_gates",
                sequence.streak(), ThousandGatesSequence.REQUIRED_PARRIES), true);
    }
    private void face() {
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
    }
    private void conceal(boolean hidden) {
        owner.setWithinThousandGates(hidden);
        owner.setInvisible(hidden || oldInvisible);
        owner.setSilent(hidden || oldSilent);
        owner.noPhysics = hidden || oldPhysics;
    }
    private void restore() {
        if (restored) return;
        restored = true;
        owner.setWithinThousandGates(false); owner.setInvisible(oldInvisible);
        owner.setSilent(oldSilent); owner.noPhysics = oldPhysics; owner.setNoGravity(oldGravity);
        owner.setDeltaMovement(Vec3.ZERO);
        visuals.portal("end", scope.id(), 1);
    }
    @Override public void stop(StopReason reason) {
        restore();
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.combatDirector().techniqueCooldown = Math.max(30, owner.combatDirector().techniqueCooldown);
    }
}
