package dev.bladetetra.challenge;

import dev.bladetetra.network.DivineSupportStatePacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.ModularTechniqueVfxPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Session-owned rescue only. No NPC, offensive casts, resurrection or global invulnerability. */
final class MikageDivineSupportManager {
    private static final AtomicInteger VISUAL_IDS = new AtomicInteger();
    final DivineDomainManager.Session session;
    final ServerLevel level;
    private final int visualId = VISUAL_IDS.incrementAndGet();
    private final Map<UUID, DivineFireRescueState> rescues = new HashMap<>();
    private final Map<UUID, Integer> rescueTargets = new HashMap<>();
    private final DivineDomainFinisher finisher;
    private boolean closed;

    MikageDivineSupportManager(DivineDomainManager.Session session, ServerLevel level) {
        this.session = session;
        this.level = level;
        finisher = new DivineDomainFinisher(this);
        session.broadcast(level.getServer(), Component.translatable("message.blade_tetra.divine.fire_ready"));
    }

    void setFinalTarget(Mob boss) { finisher.bind(boss); }
    List<ServerPlayer> players() {
        return session.players.stream().map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(p -> p != null && p.level() == level && p.isAlive()).toList();
    }
    List<Mob> enemies() {
        return session.enemies.stream().map(level::getEntity).filter(e -> e instanceof Mob && e.isAlive())
                .map(e -> (Mob) e).toList();
    }

    void tick() {
        if (closed) return;
        long now = level.getGameTime();
        List<ServerPlayer> participants = players();
        List<Mob> mobs = enemies();
        finisher.tick(now);
        rescueTargets.entrySet().removeIf(entry -> {
            if (participants.stream().anyMatch(p -> p.getUUID().equals(entry.getKey()))
                    && rescues.get(entry.getKey()).active(now)) return false;
            effect("rescue_end", new Vec3(session.originX, 64, session.originZ), entry.getValue(), 1, 0);
            return true;
        });
        for (ServerPlayer player : participants) {
            DivineFireRescueState rescue = rescues.computeIfAbsent(player.getUUID(), id -> new DivineFireRescueState());
            if (!player.isCreative() && !player.isSpectator()
                    && rescue.tryStart(player.getHealth(), player.getMaxHealth(), now)) {
                effect("rescue", player.position(), player.getId(), DivineFireRescueState.DURATION, 0);
                rescueTargets.put(player.getUUID(), player.getId());
                player.sendSystemMessage(Component.translatable("message.blade_tetra.divine.fire_rescue"));
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, .8F, 1.2F);
                status(player, rescue, true);
            }
            if (rescue.active(now)) {
                if (rescue.healingPulse(now)) player.heal(DivineFireRescueState.healingAmount(player.getMaxHealth()));
                for (Mob mob : mobs) repel(mob, player);
            }
            if (now % 20 == 0) status(player, rescue, true);
        }
        for (Mob mob : mobs) {
            if (mob.getY() < DivineDomainArenaData.FLOOR_Y - 4
                    || !DivineDomainArenaData.contains(mob.getX(), mob.getZ(), session.originX, session.originZ)) {
                var pos = DivineDomainArenaData.spawnPoint(session.originX, session.originZ, 0, 8);
                mob.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            }
            if (!participants.isEmpty() && (mob.getTarget() == null || !mob.getTarget().isAlive()
                    || mob.getTarget().level() != level || !session.players.contains(mob.getTarget().getUUID())))
                mob.setTarget(participants.get(0));
        }
    }

    private void repel(Mob mob, ServerPlayer player) {
        Vec3 delta = mob.position().subtract(player.position());
        if (Math.abs(delta.y) > 3.5 || delta.x * delta.x + delta.z * delta.z > DivineFireRescueState.RADIUS * DivineFireRescueState.RADIUS) return;
        Vec3 outward = new Vec3(delta.x, 0, delta.z);
        if (outward.lengthSqr() < .001) outward = new Vec3(1, 0, 0);
        outward = outward.normalize().scale(.35);
        mob.getNavigation().stop();
        mob.setDeltaMovement(outward.x, Math.max(0, mob.getDeltaMovement().y), outward.z);
        mob.hurtMarked = true;
    }

    boolean blocksMelee(ServerPlayer player) {
        var rescue = rescues.get(player.getUUID());
        return !closed && session.players.contains(player.getUUID()) && rescue != null && rescue.active(level.getGameTime());
    }
    void say(String text) { session.broadcast(level.getServer(), Component.literal("御影：" + text)); }
    void effect(String kind, Vec3 position, int target, int duration, float yaw) {
        var packet = new ModularTechniqueVfxPacket(new ResourceLocation("blade_tetra", "divine/" + kind),
                session.originX, DivineDomainArenaData.FLOOR_Y + 1, session.originZ,
                position.x, position.y, position.z, yaw, 1, -1, target, duration, visualId);
        for (ServerPlayer observer : level.players()) if (observer.distanceToSqr(position) < 128 * 128)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }
    private void status(ServerPlayer player, DivineFireRescueState rescue, boolean active) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DivineSupportStatePacket(session.id, active, rescue.remaining(level.getGameTime()),
                        !rescue.used(), rescue.active(level.getGameTime())));
    }
    void close() {
        if (closed) return;
        for (ServerPlayer player : players()) status(player, rescues.computeIfAbsent(player.getUUID(), id -> new DivineFireRescueState()), false);
        effect("end", new Vec3(session.originX, 64, session.originZ), -1, 1, 0);
        rescues.clear();
        rescueTargets.clear();
        closed = true;
    }
}
