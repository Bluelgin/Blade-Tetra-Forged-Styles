package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Foreground release owns movement and cancellation; native B owns attack timing. */
final class MikageGateCorridorExecution implements SkillExecution {
    private final MikageEntity owner;
    private final UUID target;
    private final Runnable committed;
    private final GateCorridorSequence sequence = new GateCorridorSequence();
    final MikageCorridorNativeCombo combo;
    private final MikageCorridorRoute[] routes = new MikageCorridorRoute[3];
    private MikageCorridorPresentation visuals;
    private boolean oldGravity, oldInvisible, oldSilent, restored, striking;
    private int age, descentTicks;
    MikageGateCorridorExecution(MikageEntity owner, ServerPlayer player, Runnable committed) {
        this.owner = owner; target = player.getUUID(); this.committed = committed;
        combo = new MikageCorridorNativeCombo(owner, this);
    }
    @Override public void start(CastScope scope) {
        oldGravity = owner.isNoGravity(); oldInvisible = owner.isInvisible(); oldSilent = owner.isSilent();
        visuals = new MikageCorridorPresentation(owner, scope.id()); scope.own(this::restore);
        scope.own(() -> owner.attackTimeline().cancel(scope));
        owner.swordWheel().recallSwordWheel((ServerLevel) owner.level(), 600);
        owner.setNoGravity(true); owner.setAction(MikageEntity.MikageAction.CAST_READY, 24);
        ServerPlayer player = player();
        if (player != null) {
            for (int i = 0; i < routes.length; i++) {
                routes[i] = MikageCorridorRoute.choose(owner, player, i);
                if (routes[i] != null) visuals.pair(routes[i], i, false);
            }
            player.displayClientMessage(Component.translatable("message.blade_tetra.mikage.gate_corridor"), true);
        }
        committed.run();
    }
    private ServerPlayer player() {
        return ((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer p ? p : null;
    }
    @Override public boolean windingUp() { return true; }
    boolean striking(ServerPlayer player) { return striking && target.equals(player.getUUID()); }
    @Override public Status tick() {
        ServerPlayer player = player();
        if (player == null || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        if (++age > 600) return Status.COMPLETE;
        owner.setTarget(player); owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
        var previous = sequence.stage(); sequence.tick();
        if (previous != sequence.stage() && sequence.stage() == GateCorridorSequence.Stage.CUE) {
            routes[sequence.pass()] = MikageCorridorRoute.choose(owner, player, sequence.pass());
            if (routes[sequence.pass()] == null) return Status.COMPLETE;
            hide(true); visuals.pair(routes[sequence.pass()], sequence.pass(), true);
        }
        var route = routes[Math.min(2, sequence.pass())];
        switch (sequence.stage()) {
            case APPROACH -> {
                if (previous == GateCorridorSequence.Stage.CUE) {
                    if (!route.entryFree(owner) || !MikageCorridorRoute.safe(owner, route.entry(), route.exit()))
                        return Status.COMPLETE;
                    owner.moveTo(route.entry().x, route.entry().y, route.entry().z);
                    hide(false); owner.setRidingPhantomSword(true); face(route.heading()); visuals.arrival();
                }
                // Approach may steer toward the moving target. Only a wholly valid new sweep is accepted.
                Vec3 heading = player.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
                if (heading.lengthSqr() > .001) {
                    Vec3 attack = player.position().add(0, .15, 0).subtract(heading.scale(4.5));
                    Vec3 exit = attack.add(heading.scale(19.5));
                    if (MikageCorridorRoute.safe(owner, owner.position(), exit)) {
                        route = new MikageCorridorRoute(route.entry(), attack, exit, heading);
                        routes[sequence.pass()] = route;
                    }
                }
                if (!move(route.attack(), .85)) return Status.COMPLETE;
                face(route.heading());
                if (owner.position().distanceToSqr(route.attack()) < .01) {
                    sequence.approachComplete(); visuals.pair(route, sequence.pass(), false);
                    owner.setAction(MikageEntity.MikageAction.COMBO_SLASH, 80);
                }
            }
            case COMBO -> {
                face(route.heading()); combo.tick(); face(route.heading());
                if (!move(route.exit(), .39)) return Status.COMPLETE;
                if (combo.finished()) { combo.stop(); sequence.comboComplete(); }
            }
            case EXIT -> {
                if (!move(route.exit(), .85)) return Status.COMPLETE;
                if (owner.position().distanceToSqr(route.exit()) < .01) {
                    owner.setRidingPhantomSword(false); hide(true); sequence.exitComplete();
                    if (sequence.stage() == GateCorridorSequence.Stage.LAND) hide(false);
                }
            }
            case LAND, DOWN -> {
                if (descend()) {
                    boolean down = sequence.stage() == GateCorridorSequence.Stage.DOWN;
                    sequence.landed();
                    if (down) owner.duel().corridorLanded(player);
                    else owner.combatDirector().techniqueCooldown = Math.max(40, owner.combatDirector().techniqueCooldown);
                    return down ? Status.COUNTERED : Status.COMPLETE;
                }
            }
            default -> { }
        }
        return Status.RUNNING;
    }
    boolean contact(EntitySlashEffect slash, AABB bounds, double reach) {
        ServerPlayer player = player();
        if (!sequence.attacking() || player == null || !owner.encounter().eligible(player)
                || !bounds.intersects(player.getBoundingBox())
                || reach > 0 && owner.distanceToSqr(player) > reach * reach
                || owner.level().clip(new ClipContext(owner.getEyePosition(), player.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS) return false;
        if (owner.duel().parryPursuit(player, owner.getEyePosition())) {
            sequence.parry(); combo.stop(); owner.setRidingPhantomSword(false);
            owner.setAction(MikageEntity.MikageAction.STAGGERED, 40); visuals.down(routes[sequence.pass()].heading());
            return true;
        }
        if (sequence.mayHit(owner.level().getGameTime())) {
            striking = true;
            try {
                if (owner.dealTrialDamage(player, (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        * slash.getDamage()), false)) sequence.hit(owner.level().getGameTime());
            } finally { striking = false; }
        }
        return true;
    }
    private boolean move(Vec3 at, double speed) {
        Vec3 next = owner.position().lerp(at, Math.min(1, speed / Math.max(.001, owner.position().distanceTo(at))));
        if (!MikageCorridorRoute.safe(owner, owner.position(), next)) return false;
        owner.setPos(next.x, next.y, next.z); owner.fallDistance = 0; return true;
    }
    private boolean descend() {
        hide(false); owner.setRidingPhantomSword(false);
        Vec3 next = owner.position().add(0, -Math.min(1, .15 + ++descentTicks * .045), 0);
        if (!MikageCorridorRoute.safe(owner, owner.position(), next)) {
            Vec3 from = owner.position(); double low = 0, high = 1;
            for (int i = 0; i < 8; i++) {
                double mid = (low + high) / 2;
                if (MikageCorridorRoute.safe(owner, from, from.lerp(next, mid))) low = mid; else high = mid;
            }
            Vec3 landing = from.lerp(next, low); owner.setPos(landing.x, landing.y, landing.z);
            owner.fallDistance = 0; return true;
        }
        owner.setPos(next.x, next.y, next.z); owner.fallDistance = 0; return false;
    }
    private void face(Vec3 heading) {
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
    }
    private void hide(boolean hidden) {
        owner.setWithinThousandGates(hidden); owner.setInvisible(hidden || oldInvisible); owner.setSilent(hidden || oldSilent);
    }
    private void restore() {
        if (restored) return; restored = true; combo.stop(); hide(false);
        owner.setRidingPhantomSword(false); owner.setNoGravity(oldGravity); owner.setDeltaMovement(Vec3.ZERO);
        owner.fallDistance = 0; visuals.end();
    }
    @Override public void stop(StopReason reason) {
        restore();
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.combatDirector().techniqueCooldown = Math.max(30, owner.combatDirector().techniqueCooldown);
    }
}
