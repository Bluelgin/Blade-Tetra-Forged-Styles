package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.ModularTechniqueVfxPacket;
import dev.bladetetra.visual.DeadThoughtVisualEvents;
import dev.bladetetra.visual.DeadThoughtVisualMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sparse notifications only. This class cannot attack, heal, spawn or retarget a combat entity. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class DeadThoughtVisuals {
    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    static void start(ServerPlayer player, Entity locked, int serial) {
        if (locked != null && (locked.level() != player.level() || !locked.isAlive()
                || player.distanceToSqr(locked) > 64 * 64)) locked = null;
        Vec3 anchor = locked == null
                ? player.position().add(player.getLookAngle().multiply(1, 0, 1).scale(3))
                : locked.position();
        Cast cast = new Cast(player.serverLevel(), serial, player.server.getTickCount(),
                locked == null ? null : locked.getUUID(), anchor);
        Cast old = CASTS.put(player.getUUID(), cast);
        if (old != null) send(player, old, DeadThoughtVisualEvents.END, null, 0);
        send(player, cast, DeadThoughtVisualEvents.START, locked, 0);
    }

    static void slash(ServerPlayer player, int serial, ResourceLocation combo) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast == null || cast.serial != serial || combo == null) return;
        String path = combo.getPath();
        int bit = path.startsWith("sakura_end_left") ? 1
                : path.startsWith("sakura_end_right") ? 2 : 4;
        if ((cast.stages & bit) != 0) return;
        cast.stages |= bit;
        send(player, cast, bit == 1 ? DeadThoughtVisualEvents.LEFT
                : bit == 2 ? DeadThoughtVisualEvents.RIGHT : DeadThoughtVisualEvents.FINAL,
                cast.target == null ? null : cast.level.getEntity(cast.target), 0);
    }

    static void erosion(ServerPlayer player, LivingEntity target, double erosion, int serial) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast != null && cast.serial == serial && cast.target == null) {
            cast.target = target.getUUID();
            cast.anchor = target.position();
        }
        // Serial zero is an ordinary SE hit, never a request for a full SA scene.
        sendPacket(player.serverLevel(), player, target, target.position(),
                DeadThoughtVisualEvents.EROSION, serial, finiteFloat(erosion));
    }

    static void soulBroken(ServerPlayer player, LivingEntity target, double erosion, int serial) {
        sendPacket(player.serverLevel(), player, target, target.position(),
                DeadThoughtVisualEvents.SOUL_BROKEN, serial, finiteFloat(erosion));
    }

    /**
     * Collapse packets carry a target-size scale rather than erosion. The client snapshots
     * position/size immediately and then owns the detached scene; target death/removal cannot
     * truncate the visual.
     */
    static void soulCollapse(ServerPlayer player, LivingEntity target, int serial) {
        int visualSerial = serial > 0 ? serial
                : 0x40000000 ^ player.server.getTickCount() ^ target.getId() * 31;
        float scale = DeadThoughtVisualMath.scale(target.getBbWidth(), target.getBbHeight());
        sendPacket(player.serverLevel(), player, target, target.position(),
                DeadThoughtVisualEvents.SOUL_COLLAPSE, visualSerial, scale);
    }

    static void tracking(ServerPlayer observer, LivingEntity target, double erosion, boolean broken) {
        ResourceLocation stage = broken ? DeadThoughtVisualEvents.SOUL_BROKEN
                : DeadThoughtVisualEvents.STATE;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet(
                observer, target, target.position(), stage, 0, finiteFloat(erosion)));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CASTS.entrySet().removeIf(entry -> {
            Cast cast = entry.getValue();
            ServerPlayer source = event.getServer().getPlayerList().getPlayer(entry.getKey());
            boolean expired = source == null || !source.isAlive() || source.level() != cast.level
                    || event.getServer().getTickCount() - cast.start >= DeadThoughtVisualMath.LIFETIME;
            if (expired && source != null && source.level() == cast.level)
                send(source, cast, DeadThoughtVisualEvents.END, null, 0);
            return expired;
        });
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { CASTS.clear(); }

    private static void send(ServerPlayer player, Cast cast, ResourceLocation stage, Entity target, float amount) {
        sendPacket(cast.level, player, target, cast.anchor, stage, cast.serial, amount);
    }

    private static void sendPacket(ServerLevel level, ServerPlayer source, Entity target,
            Vec3 anchor, ResourceLocation stage, int serial, float amount) {
        ModularTechniqueVfxPacket packet = packet(source, target, anchor, stage, serial, amount);
        // Union of source/target observers; a recipient receives each stage at most once.
        for (ServerPlayer observer : level.players()) {
            if (observer.distanceToSqr(source) <= 128 * 128 || observer.distanceToSqr(anchor) <= 128 * 128)
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
        }
    }

    private static ModularTechniqueVfxPacket packet(ServerPlayer source, Entity target, Vec3 anchor,
            ResourceLocation stage, int serial, float amount) {
        Vec3 end = target == null ? anchor : target.position();
        return new ModularTechniqueVfxPacket(stage, source.getX(), source.getY(), source.getZ(),
                end.x, end.y, end.z, source.getYRot(), amount, source.getId(),
                target == null ? -1 : target.getId(), DeadThoughtVisualMath.LIFETIME, serial);
    }

    private static float finiteFloat(double value) {
        if (!Double.isFinite(value)) return Float.MAX_VALUE;
        return (float) Math.min(Math.max(0.0D, value), Float.MAX_VALUE);
    }

    private static final class Cast {
        final ServerLevel level;
        final int serial;
        final int start;
        UUID target;
        Vec3 anchor;
        int stages;
        Cast(ServerLevel level, int serial, int start, UUID target, Vec3 anchor) {
            this.level = level; this.serial = serial; this.start = start;
            this.target = target; this.anchor = anchor;
        }
    }
    private DeadThoughtVisuals() {}
}
