package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.MikageMusicPacket;
import dev.bladetetra.network.MikageBoundaryPacket;
import dev.bladetetra.network.MikageHudPacket;
import dev.bladetetra.network.MikageDialoguePacket;
import dev.bladetetra.network.MikageVisitorDialoguePacket;
import dev.bladetetra.network.ModNetwork;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChallengeManager {
    static final Logger LOGGER = LogUtils.getLogger();
    public enum GateMode {
        NORMAL,
        REMINISCENCE,
        VISITOR
    }

    public static final ResourceKey<Level> MIRROR_REALM = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            new ResourceLocation(BladeTetra.MOD_ID, "mirror_realm"));
    public static final ResourceKey<Level> EASTER_REALM = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            new ResourceLocation(BladeTetra.MOD_ID, "easter_realm"));
    static final String PLAYER_CHALLENGE = "blade_tetra_challenge_id";
    static final String EASTER_ENTRY_TICKET = "blade_tetra_easter_entry_ticket";
    static final String EASTER_PENDING_RETURN = "blade_tetra_easter_pending_return";
    static final String GATE_LOCK = "blade_tetra_gate_lock_until";
    static final String ORIGIN_DIMENSION = "blade_tetra_origin_dimension";
    static final String ORIGIN_X = "blade_tetra_origin_x";
    static final String ORIGIN_Y = "blade_tetra_origin_y";
    static final String ORIGIN_Z = "blade_tetra_origin_z";
    static final String ORIGIN_YAW = "blade_tetra_origin_yaw";
    static final String ORIGIN_PITCH = "blade_tetra_origin_pitch";
    static final int RECONNECT_GRACE_TICKS = 600;
    private static final Map<Long, ChallengeSession> CHALLENGES = new HashMap<>();
    private static final Map<UUID, Origin> LAST_SAFE_POSITIONS = new HashMap<>();
    private static final List<ChallengeGate> GATES = new ArrayList<>();
    private static final ChallengeSlotAllocator ARENA_SLOTS = new ChallengeSlotAllocator();
    private static long nextId = 1L;
    private static MinecraftServer activeServer;
    private static boolean mirrorRealmCleaned;

    @SubscribeEvent
    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        resetForServer(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            clearSessionState();
            activeServer = null;
        }
    }

    private static void ensureActiveServer(MinecraftServer server) {
        if (activeServer != server) {
            resetForServer(server);
        }
    }

    private static void resetForServer(MinecraftServer server) {
        clearSessionState();
        activeServer = server;
        mirrorRealmCleaned = false;
    }

    private static void clearSessionState() {
        CHALLENGES.clear();
        LAST_SAFE_POSITIONS.clear();
        GATES.clear();
        ARENA_SLOTS.reset();
        nextId = 1L;
    }

    public static void openGate(ServerPlayer owner, GateMode mode) {
        ensureActiveServer(owner.getServer());
        if (isProtectedRealm(owner.level())) {
            owner.displayClientMessage(Component.translatable(
                    "message.blade_tetra.gate.already_inside").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 forward = new Vec3(owner.getLookAngle().x, 0.0D,
                owner.getLookAngle().z).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 position = owner.position().add(forward.scale(3.0D)).add(0.0D, 0.2D, 0.0D);
        GATES.removeIf(gate -> gate.owner.equals(owner.getUUID()));
        GATES.add(new ChallengeGate(owner.getUUID(), owner.level().dimension(), position, right,
                owner.level().getGameTime() + 240L, mode));
        owner.level().playSound(null, BlockPos.containing(position), SoundEvents.PORTAL_TRIGGER,
                SoundSource.PLAYERS, 0.7F, mode == GateMode.REMINISCENCE ? 0.65F
                        : mode == GateMode.VISITOR ? 0.9F : 1.15F);
        owner.displayClientMessage(Component.translatable(
                mode == GateMode.REMINISCENCE ? "message.blade_tetra.gate.echo_opened"
                        : mode == GateMode.VISITOR
                                ? "message.blade_tetra.gate.visitor_opened"
                                : "message.blade_tetra.gate.opened"), true);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ensureActiveServer(server);
        tickGates(server);
        ServerLevel mirror = server.getLevel(MIRROR_REALM);
        ServerLevel easter = server.getLevel(EASTER_REALM);
        if (mirror == null || easter == null) {
            return;
        }
        if (mirror.getGameTime() % 20L == 0L) {
            // The mirror realm is a sealed sunset, not a world whose weather
            // follows the overworld. Keep this local to the challenge level.
            mirror.setWeatherParameters(6000, 0, false, false);
        }
        if (!mirrorRealmCleaned) {
            // ChallengeSession sessions are process-local. Once the protected levels are
            // available, anything left in the mirror realm from an older process
            // is stale. Clean it exactly once instead of scanning the full realm
            // every second for the rest of the server uptime.
            cleanupOrphanedRealmEntities(mirror);
            mirrorRealmCleaned = true;
        }
        if (server.getTickCount() % 20 == 0) {
            easter.setWeatherParameters(6000, 0, false, false);
            auditRealmPlayers(server);
        }
        Iterator<ChallengeSession> iterator = CHALLENGES.values().iterator();
        while (iterator.hasNext()) {
            ChallengeSession challenge = iterator.next();
            challenge.tick(server, mirror, easter);
            if (challenge.closed) {
                iterator.remove();
                ARENA_SLOTS.release(challenge.arenaSlot);
            }
        }
    }

    private static void cleanupOrphanedRealmEntities(ServerLevel mirror) {
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : mirror.getAllEntities()) {
            if (entity instanceof MikageEntity) {
                stale.add(entity);
            } else if (entity instanceof EntityAbstractSummonedSword sword
                    && (sword.getOwner() == null
                            || sword.getPersistentData().getBoolean(
                                    "blade_tetra_mikage_attack"))) {
                stale.add(sword);
            }
        }
        stale.forEach(Entity::discard);
    }

    static void cleanupChallengeAttacks(ServerLevel mirror, ChallengeSession challenge) {
        AABB arena = new AABB(challenge.offsetX + 105.0D, 45.0D,
                challenge.offsetZ - 90.0D, challenge.offsetX + 255.0D,
                340.0D, challenge.offsetZ + 70.0D);
        mirror.getEntitiesOfClass(EntityAbstractSummonedSword.class, arena,
                        sword -> sword.getOwner() == null
                                || sword.getOwner() instanceof MikageEntity
                                || sword.getPersistentData().getBoolean(
                                        "blade_tetra_mikage_attack"))
                .forEach(Entity::discard);
    }

    private static void tickGates(MinecraftServer server) {
        Iterator<ChallengeGate> iterator = GATES.iterator();
        while (iterator.hasNext()) {
            ChallengeGate gate = iterator.next();
            ServerLevel level = server.getLevel(gate.dimension);
            if (level == null || level.getGameTime() > gate.expiresAt) {
                iterator.remove();
                continue;
            }
            ToriiParticles.render(level, gate.position, gate.right,
                    gate.mode == GateMode.REMINISCENCE, 1.0D);
            AABB entry = new AABB(gate.position.x - 2.3D, gate.position.y,
                    gate.position.z - 2.3D, gate.position.x + 2.3D,
                    gate.position.y + 3.3D, gate.position.z + 2.3D);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, entry)) {
                Vec3 relative = player.position().subtract(gate.position);
                Vec3 normal = new Vec3(-gate.right.z, 0.0D, gate.right.x);
                if (Math.abs(relative.dot(gate.right)) > 1.55D
                        || Math.abs(relative.dot(normal)) > 0.72D) {
                    continue;
                }
                if (player.isSpectator() || player.getPersistentData().contains(PLAYER_CHALLENGE)) {
                    continue;
                }
                if (player.getPersistentData().getLong(GATE_LOCK) > level.getGameTime()) {
                    continue;
                }
                long challengeId = gate.challengeId;
                if (challengeId == 0L || !CHALLENGES.containsKey(challengeId)) {
                    challengeId = nextId++;
                    gate.challengeId = challengeId;
                    int arenaSlot = ARENA_SLOTS.acquire();
                    try {
                        CHALLENGES.put(challengeId,
                                new ChallengeSession(challengeId, gate.mode, arenaSlot));
                    } catch (RuntimeException | Error failure) {
                        ARENA_SLOTS.release(arenaSlot);
                        throw failure;
                    }
                }
                ChallengeSession challenge = CHALLENGES.get(challengeId);
                if (challenge != null) {
                    challenge.join(player, server);
                }
            }
        }
    }

    public static void broadcastToChallenge(MikageEntity mikage, Component message) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null) {
            challenge.broadcast(mikage.getServer(), message);
        }
    }

    static void queueDialogue(MikageEntity mikage, MikageDialogue line) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null) {
            challenge.queueDialogue(line);
        }
    }

    static boolean tryDialogue(MikageEntity mikage, MikageDialogue line) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge != null && challenge.tryDialogue(mikage.getServer(), line);
    }

    static boolean tryVoice(MikageEntity mikage, MikageDialogue line) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge != null && challenge.tryVoice(mikage.getServer(), line);
    }

    public static void openVisitorDialogue(ServerPlayer player, MikageEntity mikage) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null && challenge.mode == GateMode.VISITOR
                && mikage.isVisitorGuide() && challenge.hasParticipant(player.getUUID())
                && player.distanceToSqr(mikage) <= 64.0D) {
            challenge.openVisitorDialogue(player);
        }
    }

    public static void handleVisitorDialogueChoice(ServerPlayer player, String choice) {
        ChallengeSession challenge = CHALLENGES.get(
                player.getPersistentData().getLong(PLAYER_CHALLENGE));
        if (challenge != null && challenge.mode == GateMode.VISITOR
                && challenge.hasParticipant(player.getUUID())) {
            challenge.handleVisitorChoice(player, choice);
        }
    }

    static void syncHud(MikageEntity mikage, int technique, int remaining, int total) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge == null) {
            return;
        }
        MikageHudPacket packet = new MikageHudPacket(true,
                mikage.getPersistentData().getLong("blade_tetra_challenge"),
                mikage.bossEventId(), mikage.getPhase(),
                mikage.getPersistentData().getBoolean("blade_tetra_reminiscence"),
                technique, Math.max(0, remaining), Math.max(0, total));
        challenge.forEachPlayer(mikage.getServer(), player -> ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), packet));
    }

    public static void onMikageDefeated(MikageEntity mikage) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null) {
            challenge.victory(mikage.getServer());
        }
    }

    public static boolean isParticipant(ServerPlayer player) {
        if (!player.level().dimension().equals(MIRROR_REALM)
                || !player.getPersistentData().contains(PLAYER_CHALLENGE)) {
            return false;
        }
        ChallengeSession challenge = CHALLENGES.get(
                player.getPersistentData().getLong(PLAYER_CHALLENGE));
        return challenge != null && challenge.hasParticipant(player.getUUID());
    }

    public static boolean isProtectedRealm(Level level) {
        return level.dimension().equals(MIRROR_REALM)
                || level.dimension().equals(EASTER_REALM);
    }

    private static void auditRealmPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isProtectedRealm(player.level())) {
                LAST_SAFE_POSITIONS.put(player.getUUID(), new Origin(
                        player.level().dimension(), player.position(),
                        player.getYRot(), player.getXRot()));
                continue;
            }
            if (!player.level().dimension().equals(EASTER_REALM)
                    || player.hasPermissions(2)) {
                continue;
            }
            ChallengeSession challenge = player.getPersistentData().contains(PLAYER_CHALLENGE)
                    ? CHALLENGES.get(player.getPersistentData().getLong(PLAYER_CHALLENGE))
                    : null;
            if (challenge == null || !challenge.isInEasterRoom(player.getUUID())) {
                returnToSafePosition(player);
            }
        }
    }

    public static boolean isParticipant(MikageEntity mikage, ServerPlayer player) {
        return isParticipant(player)
                && player.getPersistentData().getLong(PLAYER_CHALLENGE)
                        == mikage.getPersistentData().getLong("blade_tetra_challenge");
    }

    static Vec3 arenaCenter(MikageEntity mikage) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge == null
                ? mikage.position()
                : new Vec3(challenge.offsetX + 179.5D, 64.0D,
                        challenge.offsetZ - 10.0D);
    }

    static Vec3 clampMikagePosition(MikageEntity mikage, Vec3 desired) {
        ChallengeSession challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge == null || challenge.won || challenge.closed) {
            return desired;
        }
        Vec3 center = new Vec3(challenge.offsetX + 179.5D, desired.y,
                challenge.offsetZ - 10.0D);
        Vec3 offset = desired.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        double radius = 53.0D;
        if (offset.lengthSqr() <= radius * radius || offset.lengthSqr() < 0.001D) {
            return desired;
        }
        Vec3 edge = center.add(offset.normalize().scale(radius));
        return new Vec3(edge.x, desired.y, edge.z);
    }

    static void ejectDefeatedPlayer(ServerPlayer player) {
        long id = player.getPersistentData().getLong(PLAYER_CHALLENGE);
        ChallengeSession challenge = CHALLENGES.get(id);
        if (challenge != null) {
            challenge.defeatPlayer(player);
        } else {
            returnFromPersistentOrigin(player);
        }
    }

    @SubscribeEvent
    public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensureActiveServer(player.getServer());
            if (player.level().dimension().equals(EASTER_REALM)
                    || player.getPersistentData().getBoolean(EASTER_PENDING_RETURN)) {
                returnFromPersistentOrigin(player);
                return;
            }
            if (!player.level().dimension().equals(MIRROR_REALM)) {
                return;
            }
            boolean hadChallenge = player.getPersistentData().contains(PLAYER_CHALLENGE);
            ChallengeSession challenge = hadChallenge
                    ? CHALLENGES.get(player.getPersistentData().getLong(PLAYER_CHALLENGE))
                    : null;
            if (challenge == null || !challenge.reconnect(player.getUUID())) {
                returnFromPersistentOrigin(player);
                if (hadChallenge) {
                    player.sendSystemMessage(Component.translatable(
                            "message.blade_tetra.challenge.failed"));
                }
            } else {
                if (challenge.mode != GateMode.VISITOR
                        && challenge.bossSpawned && !challenge.won) {
                    startMusic(player, challenge.id);
                    showBoundary(player, challenge);
                }
            }
        }
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().contains(PLAYER_CHALLENGE)) {
            ChallengeSession challenge = CHALLENGES.get(
                    player.getPersistentData().getLong(PLAYER_CHALLENGE));
            if (challenge != null) {
                if (player.level().dimension().equals(EASTER_REALM)) {
                    player.getPersistentData().putBoolean(EASTER_PENDING_RETURN, true);
                    challenge.detachForEasterLogout(player);
                } else {
                    challenge.markDisconnected(player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ensureActiveServer(player.getServer());
        if (event.getTo().equals(EASTER_REALM)) {
            ChallengeSession challenge = player.getPersistentData().contains(PLAYER_CHALLENGE)
                    ? CHALLENGES.get(player.getPersistentData().getLong(PLAYER_CHALLENGE))
                    : null;
            boolean ticket = player.getPersistentData().getBoolean(EASTER_ENTRY_TICKET);
            player.getPersistentData().remove(EASTER_ENTRY_TICKET);
            if (!player.hasPermissions(2)
                    && (!ticket || challenge == null
                    || !challenge.isInEasterRoom(player.getUUID()))) {
                returnToSafePosition(player);
            }
            return;
        }
        if (event.getTo().equals(MIRROR_REALM)) {
            if (!isParticipant(player)) {
                returnFromPersistentOrigin(player);
                player.sendSystemMessage(Component.translatable(
                        "message.blade_tetra.challenge.failed"));
            }
            return;
        }
        if (event.getFrom().equals(MIRROR_REALM)
                && player.getPersistentData().contains(PLAYER_CHALLENGE)) {
            ChallengeSession challenge = CHALLENGES.get(
                    player.getPersistentData().getLong(PLAYER_CHALLENGE));
            if (event.getTo().equals(EASTER_REALM)
                    && challenge != null
                    && challenge.isInEasterRoom(player.getUUID())) {
                return;
            }
            if (challenge != null && challenge.hasParticipant(player.getUUID())) {
                challenge.forfeitOutside(player);
                player.sendSystemMessage(Component.translatable(
                        "message.blade_tetra.challenge.failed"));
            }
            return;
        }
        if (event.getFrom().equals(EASTER_REALM)
                && player.getPersistentData().contains(PLAYER_CHALLENGE)) {
            ChallengeSession challenge = CHALLENGES.get(
                    player.getPersistentData().getLong(PLAYER_CHALLENGE));
            if (challenge != null) {
                // Commands and third-party teleporters may deliberately take a visitor
                // elsewhere. Their destination wins; only the challenge session ends.
                challenge.forfeitOutside(player);
            } else {
                clearPersistentChallenge(player, player.level().getGameTime());
            }
        }
    }

    static void startMusic(ServerPlayer player, long challengeId) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageMusicPacket.start(challengeId));
    }

    static void stopMusic(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageMusicPacket.stop());
    }

    static void showBoundary(ServerPlayer player, ChallengeSession challenge) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageBoundaryPacket.show(challenge.id,
                        challenge.offsetX + 124.0D, challenge.offsetX + 235.0D,
                        challenge.offsetZ - 67.0D, challenge.offsetZ + 47.0D,
                        64.03D));
    }

    static void hideBoundary(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageBoundaryPacket.hide());
    }

    static void hideHud(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageHudPacket.hide());
    }

    static void savePersistentOrigin(ServerPlayer player) {
        var tag = player.getPersistentData();
        tag.putString(ORIGIN_DIMENSION, player.level().dimension().location().toString());
        tag.putDouble(ORIGIN_X, player.getX());
        tag.putDouble(ORIGIN_Y, player.getY());
        tag.putDouble(ORIGIN_Z, player.getZ());
        tag.putFloat(ORIGIN_YAW, player.getYRot());
        tag.putFloat(ORIGIN_PITCH, player.getXRot());
    }

    static void returnFromPersistentOrigin(ServerPlayer player) {
        var tag = player.getPersistentData();
        ResourceKey<Level> key = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.tryParse(tag.getString(ORIGIN_DIMENSION)) == null
                        ? Level.OVERWORLD.location()
                        : ResourceLocation.tryParse(tag.getString(ORIGIN_DIMENSION)));
        ServerLevel destination = player.getServer().getLevel(key);
        if (destination == null) {
            destination = player.getServer().overworld();
        }
        if (tag.contains(ORIGIN_X)) {
            player.teleportTo(destination, tag.getDouble(ORIGIN_X), tag.getDouble(ORIGIN_Y),
                    tag.getDouble(ORIGIN_Z), tag.getFloat(ORIGIN_YAW), tag.getFloat(ORIGIN_PITCH));
        } else {
            BlockPos spawn = destination.getSharedSpawnPos();
            player.teleportTo(destination, spawn.getX() + 0.5D, spawn.getY() + 1.0D,
                    spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
        clearPersistentChallenge(player, destination.getGameTime());
    }

    private static void returnToSafePosition(ServerPlayer player) {
        if (player.getPersistentData().contains(ORIGIN_DIMENSION)) {
            returnFromPersistentOrigin(player);
            return;
        }
        Origin safe = LAST_SAFE_POSITIONS.get(player.getUUID());
        ServerLevel destination = safe == null ? player.getServer().overworld()
                : player.getServer().getLevel(safe.dimension);
        if (destination == null) {
            destination = player.getServer().overworld();
        }
        if (safe != null) {
            player.teleportTo(destination, safe.position.x, safe.position.y,
                    safe.position.z, safe.yaw, safe.pitch);
        } else {
            BlockPos spawn = destination.getSharedSpawnPos();
            player.teleportTo(destination, spawn.getX() + 0.5D, spawn.getY() + 1.0D,
                    spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
        clearPersistentChallenge(player, destination.getGameTime());
    }

    static void clearPersistentChallenge(ServerPlayer player, long now) {
        var tag = player.getPersistentData();
        tag.remove(PLAYER_CHALLENGE);
        tag.remove(EASTER_ENTRY_TICKET);
        tag.remove(EASTER_PENDING_RETURN);
        tag.putLong(GATE_LOCK, now + 100L);
        tag.remove(ORIGIN_DIMENSION);
        tag.remove(ORIGIN_X);
        tag.remove(ORIGIN_Y);
        tag.remove(ORIGIN_Z);
        tag.remove(ORIGIN_YAW);
        tag.remove(ORIGIN_PITCH);
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }





    record Origin(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
    }

    private ChallengeManager() {
    }
}
