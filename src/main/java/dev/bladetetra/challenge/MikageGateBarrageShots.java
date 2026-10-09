package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.GateBarrageSequence;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A bounded live set, not thousands of cleanup callbacks accumulated by a continuous release. */
final class MikageGateBarrageShots {
    private final MikageEntity owner;
    private final dev.bladetetra.challenge.mikage.CastScope scope;
    private final Set<MikageGateSwordEntity> live = new HashSet<>();
    private Vec3 aim;
    private int volley;
    MikageGateBarrageShots(MikageEntity owner, dev.bladetetra.challenge.mikage.CastScope scope, Vec3 aim) { this.owner = owner; this.scope = scope; this.aim = aim; }
    void tick(ServerPlayer player) {
        live.removeIf(net.minecraft.world.entity.Entity::isRemoved);
        aim = aim.lerp(player.position().add(0, 1, 0), .12);
    }
    int liveCount() { return live.size(); }
    boolean owns(MikageGateSwordEntity sword) { return live.contains(sword); }
    void volley(MikageGateBarragePlacement gate) {
        ServerLevel level = (ServerLevel) owner.level();
        if (live.size() > GateBarrageSequence.MAX_SWORDS - 5) return;
        for (int i = 0; i < 5; i++) {
            double lane = (i - 2) * 2.2;
            Vec3 origin = gate.base().add(gate.right().scale(lane)).add(gate.forward().scale(.35))
                    .add(0, 1 + ((volley + i) % 3) * .25, 0);
            Vec3 destination = aim.add(gate.right().scale((i - 2) * .3));
            var sword = new MikageGateSwordEntity(ModEntities.MIKAGE_GATE_SWORD.get(), level);
            sword.configure(owner, scope); sword.setPos(origin);
            Vec3 direction = destination.subtract(origin).normalize();
            float speed = (float) Math.min(1.65, .85 + Math.max(0, destination.distanceTo(origin) - 24) / 90);
            sword.shoot(direction.x, direction.y, direction.z, speed, 0); sword.setRoll(i * 25 + volley * 7);
            live.add(sword);
            if (!level.addFreshEntity(sword)) { live.remove(sword); sword.discard(); }
        }
        if (volley++ % 3 == 0) level.playSound(null, gate.base().x, gate.base().y + 1.5, gate.base().z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, .65F, 1.3F);
    }
    void clear() { live.forEach(net.minecraft.world.entity.Entity::discard); live.clear(); }
}
