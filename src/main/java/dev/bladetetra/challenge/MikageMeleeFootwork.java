package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;

/** Steps belong to the phrase. Each pursuit locks before its slash and stops at real terrain. */
final class MikageMeleeFootwork {
    private final MikageEntity owner;
    private final MikageMove move;
    private Vec3 heading = new Vec3(0, 0, 1), lastTarget, lockedAim;
    private boolean pursuing;
    MikageMeleeFootwork(MikageEntity owner, MikageMove move) { this.owner = owner; this.move = move; }
    void tick(ServerPlayer player, MeleeRhythm rhythm) {
        int remaining = rhythm.untilNextBeat();
        Vec3 towards = player.position().subtract(owner.position()).multiply(1, 0, 1);
        double distance = towards.length();
        boolean retreating = lastTarget != null && distance > .001
                && player.position().subtract(lastTarget).dot(towards.normalize()) > .25;
        if (rhythm.beginPursuit(retreating, distance)) {
            pursuing = true;
            owner.setAction(MikageEntity.MikageAction.IAIDO_READY, 6);
            owner.level().playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER,
                    SoundSource.HOSTILE, .9F, .8F);
        }
        // The opener has its full tell; follow-up blades retarget only during their next tell.
        if (rhythm.beat() < 0 && rhythm.age() <= move.windup - 8
                || rhythm.beat() >= 0 && remaining <= 6 && remaining > 4) {
            lockedAim = player.position();
            if (move == MikageMove.ZANSHIN) lockedAim = lockedAim.add(owner.encounter().observedRoute(player).scale(8));
            Vec3 aim = lockedAim.subtract(owner.position()).multiply(1, 0, 1);
            if (aim.lengthSqr() > .001) heading = aim.normalize();
        }
        face();
        if (remaining > 0 && remaining <= 6 && rhythm.beat() >= 0) {
            if (pursuing && remaining > 2) step(player, .7);
            if (move == MikageMove.CLEAVE && rhythm.beat() == 1)
                owner.setAction(MikageEntity.MikageAction.IAIDO_READY, remaining);
        } else if (rhythm.windingUp() && (move != MikageMove.IAIDO && move != MikageMove.DUEL
                || rhythm.age() >= move.windup - 8)) step(player, move == MikageMove.IAIDO ? 1 : .6);
    }
    void released(ServerPlayer player) { lastTarget = player.position(); pursuing = false; }
    Vec3 heading() { return heading; }
    Vec3 aim() { return lockedAim.add(0, .8, 0); }
    private void step(ServerPlayer player, double speed) {
        if (!owner.onGround() || owner.position().multiply(1, 0, 1)
                .distanceToSqr(player.position().multiply(1, 0, 1)) < 3.2 * 3.2) return;
        Vec3 next = owner.position().add(heading.scale(speed));
        if (MikageFootworkRoutes.clear(owner, next)) owner.setPos(next.x, next.y, next.z);
    }
    void face() {
        float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
    }
}
