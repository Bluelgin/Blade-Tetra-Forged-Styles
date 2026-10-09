package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import java.util.UUID;

/** Committed short attacks, visible ground approach and real native projectiles. */
final class MikageSwordplayExecution implements SkillExecution {
    private final MikageEntity owner;
    final MikageMove move;
    private final UUID target;
    private final Runnable committed;
    private CastScope scope;
    private Vec3 heading, lockedAim;
    private int age, released;
    private boolean parried, oldGravity;
    MikageSwordplayExecution(MikageEntity owner, ServerPlayer target, MikageMove move, Runnable committed) {
        this.owner = owner; this.target = target.getUUID(); this.move = move; this.committed = committed;
    }
    @Override public void start(CastScope scope) {
        this.scope = scope; oldGravity = owner.isNoGravity(); scope.own(() -> owner.setNoGravity(oldGravity));
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        owner.setAction(move.melee() ? MikageEntity.MikageAction.IAIDO_READY : MikageEntity.MikageAction.CAST_READY, move.windup);
        sound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.3F);
    }
    @Override public boolean windingUp() { return age < move.windup; }
    private ServerPlayer player() { return ((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer p ? p : null; }
    @Override public Status tick() {
        ServerPlayer player = player();
        if (player == null || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO); ++age;
        if (age <= move.windup - 8) {
            lockedAim = player.position().add(0, .8, 0);
            if (move == MikageMove.ZANSHIN) lockedAim = lockedAim.add(owner.encounter().observedRoute(player).scale(8));
            heading = lockedAim.subtract(owner.position()).multiply(1, 0, 1).normalize();
            if (heading.lengthSqr() < .001) heading = new Vec3(0, 0, 1);
        }
        if (heading != null) face();
        if (move.melee() && age < move.windup
                && (move != MikageMove.IAIDO && move != MikageMove.DUEL || age >= move.windup - 8))
            approach(player, move == MikageMove.IAIDO ? 1.0 : move == MikageMove.DUEL ? .85 : .5);
        if (move == MikageMove.RAIN && age < move.windup) {
            owner.setNoGravity(true);
            Vec3 next = owner.position().add(0, .12, 0);
            if (MikageCorridorRoute.safe(owner, owner.position(), next)) owner.setPos(next.x, next.y, next.z);
        }
        if (!move.melee() && lockedAim != null && age < move.windup) MikageNativeAttacks.tell(owner, lockedAim, move);
        int strikeAge = age - move.windup;
        if (move == MikageMove.CHASE_RAIN && (strikeAge == 6 || strikeAge == 20 || strikeAge == 36))
            lockedAim = player.position().add(0, .8, 0);
        if (move == MikageMove.ZANSHIN && windingUp() || move == MikageMove.CHASE_RAIN && strikeAge >= 0)
            MikageNativeAttacks.tell(owner, lockedAim, MikageMove.CUT);
        int[] releases = move.releases();
        for (int i = 0; i < releases.length; i++) if (strikeAge == releases[i]) {
            if (released++ == 0) committed.run();
            owner.setAction(move.melee() ? (move == MikageMove.CLEAVE ? MikageEntity.MikageAction.HEAVY_CLEAVE
                    : MikageEntity.MikageAction.COMBO_SLASH) : MikageEntity.MikageAction.CAST_SLASH, 12);
            MikageNativeCombat.run(owner, scope, rule(), () -> MikageNativeAttacks.release(owner, scope, move, lockedAim, heading, strikeAge));
            sound(SoundEvents.PLAYER_ATTACK_SWEEP, move == MikageMove.CLEAVE ? .6F : 1.1F);
        }
        if (move == MikageMove.RAIN && strikeAge >= 24) {
            owner.setNoGravity(false); owner.setDeltaMovement(0, -.35, 0); owner.fallDistance = 0;
        }
        return parried ? Status.COUNTERED : strikeAge >= releases[releases.length - 1] + (move.melee() ? move.recovery : Math.max(40, move.recovery)) ? Status.COMPLETE : Status.RUNNING;
    }
    private MikageNativeCombat.Rule rule() {
        return new MikageNativeCombat.Rule(target, move.melee(), false, (player, source) -> {
            if (move.melee() && owner.duel().parry(player, owner.getEyePosition())) {
                parried = true; MikageNativeCombat.discard(scope); owner.duel().rewardOpening(20); return false;
            }
            return true;
        }, (player, source) -> { });
    }
    private void approach(ServerPlayer player, double speed) {
        if (owner.position().multiply(1, 0, 1).distanceToSqr(player.position().multiply(1, 0, 1)) < 3.5 * 3.5) return;
        Vec3 next = owner.position().add(heading.scale(speed));
        if (MikageFootworkRoutes.clear(owner, next)) owner.setPos(next.x, next.y, next.z);
    }
    private void face() {
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
    }
    private void sound(SoundEvent event, float pitch) {
        ((ServerLevel) owner.level()).playSound(null, owner.blockPosition(), event, SoundSource.HOSTILE, .8F, pitch);
    }
    @Override public void stop(StopReason reason) {
        if (released > 0 && move.melee() && (reason == StopReason.COMPLETE || reason == StopReason.COUNTERED))
            owner.movement().meleeExchangeCompleted(target);
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.setDeltaMovement(Vec3.ZERO); owner.fallDistance = 0;
        owner.combatDirector().techniqueCooldown = Math.max(18, owner.combatDirector().techniqueCooldown);
    }
}
