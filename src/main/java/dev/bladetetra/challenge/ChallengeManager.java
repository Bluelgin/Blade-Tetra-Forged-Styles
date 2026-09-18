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
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private static final String EASTER_ENTRY_TICKET = "blade_tetra_easter_entry_ticket";
    private static final String EASTER_PENDING_RETURN = "blade_tetra_easter_pending_return";
    private static final String GATE_LOCK = "blade_tetra_gate_lock_until";
    private static final String ORIGIN_DIMENSION = "blade_tetra_origin_dimension";
    private static final String ORIGIN_X = "blade_tetra_origin_x";
    private static final String ORIGIN_Y = "blade_tetra_origin_y";
    private static final String ORIGIN_Z = "blade_tetra_origin_z";
    private static final String ORIGIN_YAW = "blade_tetra_origin_yaw";
    private static final String ORIGIN_PITCH = "blade_tetra_origin_pitch";
    private static final int RECONNECT_GRACE_TICKS = 600;
    private static final Map<Long, Challenge> CHALLENGES = new HashMap<>();
    private static final Map<UUID, Origin> LAST_SAFE_POSITIONS = new HashMap<>();
    private static final List<Gate> GATES = new ArrayList<>();
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
        GATES.add(new Gate(owner.getUUID(), owner.level().dimension(), position, right,
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
            // Challenge sessions are process-local. Once the protected levels are
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
        Iterator<Challenge> iterator = CHALLENGES.values().iterator();
        while (iterator.hasNext()) {
            Challenge challenge = iterator.next();
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

    private static void cleanupChallengeAttacks(ServerLevel mirror, Challenge challenge) {
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
        Iterator<Gate> iterator = GATES.iterator();
        while (iterator.hasNext()) {
            Gate gate = iterator.next();
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
                                new Challenge(challengeId, gate.mode, arenaSlot));
                    } catch (RuntimeException | Error failure) {
                        ARENA_SLOTS.release(arenaSlot);
                        throw failure;
                    }
                }
                Challenge challenge = CHALLENGES.get(challengeId);
                if (challenge != null) {
                    challenge.join(player, server);
                }
            }
        }
    }

    public static void broadcastToChallenge(MikageEntity mikage, Component message) {
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null) {
            challenge.broadcast(mikage.getServer(), message);
        }
    }

    static void queueDialogue(MikageEntity mikage, MikageDialogue line) {
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null) {
            challenge.queueDialogue(line);
        }
    }

    static boolean tryDialogue(MikageEntity mikage, MikageDialogue line) {
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge != null && challenge.tryDialogue(mikage.getServer(), line);
    }

    static boolean tryVoice(MikageEntity mikage, MikageDialogue line) {
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge != null && challenge.tryVoice(mikage.getServer(), line);
    }

    public static void openVisitorDialogue(ServerPlayer player, MikageEntity mikage) {
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        if (challenge != null && challenge.mode == GateMode.VISITOR
                && mikage.isVisitorGuide() && challenge.hasParticipant(player.getUUID())
                && player.distanceToSqr(mikage) <= 64.0D) {
            challenge.openVisitorDialogue(player);
        }
    }

    public static void handleVisitorDialogueChoice(ServerPlayer player, String choice) {
        Challenge challenge = CHALLENGES.get(
                player.getPersistentData().getLong(PLAYER_CHALLENGE));
        if (challenge != null && challenge.mode == GateMode.VISITOR
                && challenge.hasParticipant(player.getUUID())) {
            challenge.handleVisitorChoice(player, choice);
        }
    }

    static void syncHud(MikageEntity mikage, int technique, int remaining, int total) {
        Challenge challenge = CHALLENGES.get(
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
        Challenge challenge = CHALLENGES.get(
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
        Challenge challenge = CHALLENGES.get(
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
            Challenge challenge = player.getPersistentData().contains(PLAYER_CHALLENGE)
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
        Challenge challenge = CHALLENGES.get(
                mikage.getPersistentData().getLong("blade_tetra_challenge"));
        return challenge == null
                ? mikage.position()
                : new Vec3(challenge.offsetX + 179.5D, 64.0D,
                        challenge.offsetZ - 10.0D);
    }

    static Vec3 clampMikagePosition(MikageEntity mikage, Vec3 desired) {
        Challenge challenge = CHALLENGES.get(
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
        Challenge challenge = CHALLENGES.get(id);
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
            Challenge challenge = hadChallenge
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
            Challenge challenge = CHALLENGES.get(
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
            Challenge challenge = player.getPersistentData().contains(PLAYER_CHALLENGE)
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
            Challenge challenge = CHALLENGES.get(
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
            Challenge challenge = CHALLENGES.get(
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

    private static void startMusic(ServerPlayer player, long challengeId) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageMusicPacket.start(challengeId));
    }

    private static void stopMusic(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageMusicPacket.stop());
    }

    private static void showBoundary(ServerPlayer player, Challenge challenge) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageBoundaryPacket.show(challenge.id,
                        challenge.offsetX + 124.0D, challenge.offsetX + 235.0D,
                        challenge.offsetZ - 67.0D, challenge.offsetZ + 47.0D,
                        64.03D));
    }

    private static void hideBoundary(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageBoundaryPacket.hide());
    }

    private static void hideHud(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                MikageHudPacket.hide());
    }

    private static void savePersistentOrigin(ServerPlayer player) {
        var tag = player.getPersistentData();
        tag.putString(ORIGIN_DIMENSION, player.level().dimension().location().toString());
        tag.putDouble(ORIGIN_X, player.getX());
        tag.putDouble(ORIGIN_Y, player.getY());
        tag.putDouble(ORIGIN_Z, player.getZ());
        tag.putFloat(ORIGIN_YAW, player.getYRot());
        tag.putFloat(ORIGIN_PITCH, player.getXRot());
    }

    private static void returnFromPersistentOrigin(ServerPlayer player) {
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

    private static void clearPersistentChallenge(ServerPlayer player, long now) {
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

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static final class Gate {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final Vec3 position;
        final Vec3 right;
        final long expiresAt;
        final GateMode mode;
        long challengeId;

        Gate(UUID owner, ResourceKey<Level> dimension, Vec3 position, Vec3 right,
                long expiresAt, GateMode mode) {
            this.owner = owner;
            this.dimension = dimension;
            this.position = position;
            this.right = right;
            this.expiresAt = expiresAt;
            this.mode = mode;
        }
    }

    private static final class Challenge {
        final long id;
        final GateMode mode;
        final boolean reminiscence;
        final int arenaSlot;
        final int offsetX;
        final int offsetZ;
        final Set<UUID> participants = new HashSet<>();
        final Map<UUID, Origin> origins = new HashMap<>();
        final Map<UUID, Integer> disconnectedTicks = new HashMap<>();
        final Set<UUID> visitorArrivals = new HashSet<>();
        final Set<UUID> easterRoomPlayers = new HashSet<>();
        final Map<UUID, String> visitorDialogueNodes = new HashMap<>();
        int buildIndex;
        int easterBuildIndex;
        int introTicks = -1;
        MikageDialogue pendingIntroSecond;
        boolean built;
        boolean easterBuilt;
        boolean bossSpawned;
        UUID bossEntityId;
        float lastBossHealthFraction = 1.0F;
        int bossMissingTicks;
        boolean won;
        boolean closed;
        int victoryTeleportTicks = -1;
        int noPlayerTicks;
        int dialogueTicks;
        final Deque<MikageDialogue> dialogueQueue = new ArrayDeque<>();

        Challenge(long id, GateMode mode, int arenaSlot) {
            this.id = id;
            this.mode = mode;
            this.reminiscence = mode == GateMode.REMINISCENCE;
            this.arenaSlot = arenaSlot;
            this.offsetX = (arenaSlot % 32) * 768;
            this.offsetZ = (arenaSlot / 32) * 768;
        }

        void join(ServerPlayer player, MinecraftServer server) {
            savePersistentOrigin(player);
            origins.putIfAbsent(player.getUUID(), new Origin(
                    player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
            participants.add(player.getUUID());
            disconnectedTicks.remove(player.getUUID());
            player.getPersistentData().putLong(PLAYER_CHALLENGE, id);
            ServerLevel mirror = server.getLevel(MIRROR_REALM);
            if (mirror != null) {
                player.teleportTo(mirror, offsetX + 0.5D, 64.0D, offsetZ + 0.5D,
                        -90.0F, 0.0F);
                player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.challenge.joined"), false);
                if (mode != GateMode.VISITOR && bossSpawned && !won) {
                    startMusic(player, id);
                    showBoundary(player, this);
                }
            }
        }

        void tick(MinecraftServer server, ServerLevel mirror, ServerLevel easter) {
            tickDisconnected();
            if (participants.isEmpty()) {
                mirror.getEntitiesOfClass(MikageEntity.class,
                                new AABB(offsetX - 32, 50, offsetZ - 172,
                                        offsetX + 290, 230, offsetZ + 152),
                                entity -> entity.getPersistentData().getLong(
                                        "blade_tetra_challenge") == id)
                        .forEach(MikageEntity::discard);
                cleanupChallengeAttacks(mirror, this);
                closed = true;
                return;
            }
            tickDialogue(server);
            if (!built) {
                if (hasCompletedArena(mirror)) {
                    built = true;
                    buildIndex = MirrorArenaData.entries().size();
                }
            }
            if (!built) {
                build(mirror);
                if (!built) {
                    forEachPlayer(server, player -> {
                        double x = Mth.clamp(player.getX(), offsetX - 8.0D,
                                offsetX + 3.0D);
                        double z = Mth.clamp(player.getZ(), offsetZ - 12.0D,
                                offsetZ + 12.0D);
                        double y = Math.max(64.0D, player.getY());
                        if (x != player.getX() || z != player.getZ() || y != player.getY()) {
                            player.teleportTo(mirror, x, y, z,
                                    player.getYRot(), player.getXRot());
                        }
                    });
                    return;
                } else {
                    markArenaComplete(mirror);
                }
            }

            forEachPlayer(server, player -> keepInsidePreparedArea(player, mirror));
            forEachEasterPlayer(server, player -> keepInsideEasterArea(player, easter));

            if (mode == GateMode.VISITOR) {
                tickVisitor(server, mirror, easter);
                return;
            }

            tickBossPresence(mirror);

            if (!bossSpawned && introTicks < 0 && anyPlayer(server,
                    player -> player.getX() >= offsetX + 158.0D)) {
                MikageDialogue first = reminiscence
                        ? MikageDialogue.REMINISCENCE_INTRO : MikageDialogue.INTRO_1;
                MikageDialogue second = reminiscence ? null
                        : hasAwakenedAkatsuki(server)
                                ? MikageDialogue.AKATSUKI : MikageDialogue.INTRO_2;
                pendingIntroSecond = second;
                speakNow(server, first);
                introTicks = first.holdTicks()
                        + (second == null ? 0 : second.holdTicks());
            }
            if (introTicks >= 0 && !bossSpawned) {
                introTicks--;
                if (pendingIntroSecond != null
                        && introTicks == pendingIntroSecond.holdTicks()) {
                    speakNow(server, pendingIntroSecond);
                    pendingIntroSecond = null;
                } else if (introTicks == 0) {
                    spawnBoss(mirror);
                }
            }
            if (!bossSpawned) {
                renderVermilionPath(mirror);
            } else if (!won) {
                forEachPlayer(server, player -> {
                    if (player.isCreative()) {
                        return;
                    }
                    double centerX = offsetX + 179.5D;
                    double centerZ = offsetZ - 10.0D;
                    double dx = player.getX() - centerX;
                    double dz = player.getZ() - centerZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double radius = 55.0D;
                    if (distance > radius) {
                        double factor = radius / Math.max(distance, 0.001D);
                        double x = centerX + dx * factor;
                        double z = centerZ + dz * factor;
                        player.teleportTo(mirror, x, Math.max(64.0D, player.getY()), z,
                                player.getYRot(), player.getXRot());
                    }
                });
            }
            if (won) {
                if (victoryTeleportTicks > 0 && --victoryTeleportTicks == 0) {
                    forEachPlayer(server, player -> player.teleportTo(mirror,
                            offsetX - 1.5D, 64.0D, offsetZ + 0.5D,
                            90.0F, 0.0F));
                }
                renderReturn(mirror);
                forEachPlayer(server, player -> {
                    if (player.position().distanceToSqr(
                            offsetX - 4.5D, 64.0D, offsetZ + 0.5D) < 6.25D) {
                        returnPlayer(player);
                    }
                });
                if (participants.isEmpty()) {
                    cleanupChallengeAttacks(mirror, this);
                    closed = true;
                }
            } else if (bossSpawned) {
                if (anyPlayer(server, player -> true)) {
                    noPlayerTicks = 0;
                } else if (++noPlayerTicks > 6000) {
                    mirror.getEntitiesOfClass(MikageEntity.class,
                                    new AABB(offsetX - 32, 50, offsetZ - 172,
                                            offsetX + 290, 230, offsetZ + 152),
                                    entity -> entity.getPersistentData().getLong(
                                            "blade_tetra_challenge") == id)
                            .forEach(MikageEntity::discard);
                    cleanupChallengeAttacks(mirror, this);
                    closed = true;
                }
            }
        }

        void build(ServerLevel mirror) {
            List<MirrorArenaData.Entry> blocks = MirrorArenaData.buildOrder();
            int limit = Math.min(blocks.size(), buildIndex
                    + GameplayConfig.MIRROR_ARENA_BLOCKS_PER_TICK.get());
            for (; buildIndex < limit; buildIndex++) {
                MirrorArenaData.Entry entry = blocks.get(buildIndex);
                mirror.setBlock(new BlockPos(offsetX + entry.x(), 63 + entry.y(),
                        offsetZ + entry.z()), entry.state(), 2 | 16);
            }
            if (buildIndex >= blocks.size()) {
                built = true;
            }
        }

        void keepInsidePreparedArea(ServerPlayer player, ServerLevel mirror) {
            if (player.getX() < offsetX - 20.0D || player.getX() > offsetX + 270.0D
                    || player.getZ() < offsetZ - 160.0D
                    || player.getZ() > offsetZ + 140.0D || player.getY() < 55.0D) {
                player.teleportTo(mirror, offsetX + 0.5D, 64.0D,
                        offsetZ + 0.5D, -90.0F, 0.0F);
            }
        }

        void keepInsideEasterArea(ServerPlayer player, ServerLevel easter) {
            if (player.getX() < easterOriginX() - 8.0D
                    || player.getX() > easterOriginX() + 281.0D
                    || player.getZ() < easterOriginZ() - 8.0D
                    || player.getZ() > easterOriginZ() + 228.0D
                    || player.getY() < 55.0D || player.getY() > 216.0D) {
                teleportToEasterEntrance(player, easter);
            }
        }

        boolean hasCompletedArena(ServerLevel mirror) {
            return mirror.getBlockState(completionMarker(mirror)).is(Blocks.BEDROCK)
                    && mirror.getBlockState(paletteMarker(mirror))
                            .is(Blocks.RED_NETHER_BRICKS);
        }

        void markArenaComplete(ServerLevel mirror) {
            mirror.setBlock(completionMarker(mirror), Blocks.BEDROCK.defaultBlockState(), 2 | 16);
            mirror.setBlock(paletteMarker(mirror),
                    Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
        }

        BlockPos completionMarker(ServerLevel mirror) {
            // Hidden below the ice layer. Change this signature if the bundled arena format changes.
            return new BlockPos(offsetX, mirror.getMinBuildHeight() + 1, offsetZ);
        }

        BlockPos paletteMarker(ServerLevel mirror) {
            // Palette v1: mangrove vermilion, blackstone roof and neutral stonework.
            return new BlockPos(offsetX, mirror.getMinBuildHeight() + 2, offsetZ);
        }

        void spawnBoss(ServerLevel mirror) {
            mirror.getEntitiesOfClass(MikageEntity.class,
                            new AABB(offsetX - 32, 50, offsetZ - 172,
                                    offsetX + 290, 230, offsetZ + 152))
                    .forEach(MikageEntity::discard);
            MikageEntity mikage = ModEntities.MIKAGE.get().create(mirror);
            if (mikage == null) {
                return;
            }
            mikage.moveTo(offsetX + 172.5D, 64.0D, offsetZ - 2.5D, -90.0F, 0.0F);
            List<ServerPlayer> party = participants.stream()
                    .map(uuid -> mirror.getServer().getPlayerList().getPlayer(uuid))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            mikage.configureForParty(party, reminiscence, id);
            mikage.prepareOpening(reminiscence
                    ? MikageDialogue.REMINISCENCE_BEGIN.holdTicks() + 10
                    : MikageDialogue.BEGIN.holdTicks() + 10);
            mikage.restrictTo(new BlockPos(offsetX + 179, 64, offsetZ - 10), 64);
            bossSpawned = mirror.addFreshEntity(mikage);
            if (!bossSpawned) {
                return;
            }
            bossEntityId = mikage.getUUID();
            lastBossHealthFraction = 1.0F;
            bossMissingTicks = 0;
            forEachPlayer(mirror.getServer(), player -> {
                startMusic(player, id);
                showBoundary(player, this);
            });
            speakNow(mirror.getServer(), reminiscence
                    ? MikageDialogue.REMINISCENCE_BEGIN : MikageDialogue.BEGIN);
        }

        void tickBossPresence(ServerLevel mirror) {
            if (!bossSpawned || won) {
                return;
            }
            Entity tracked = bossEntityId == null ? null : mirror.getEntity(bossEntityId);
            MikageEntity mikage = tracked instanceof MikageEntity candidate
                    && candidate.isAlive()
                    && candidate.getPersistentData().getLong("blade_tetra_challenge") == id
                    ? candidate : null;
            if (mikage == null) {
                mikage = mirror.getEntitiesOfClass(MikageEntity.class,
                                new AABB(offsetX + 120, 50, offsetZ - 72,
                                        offsetX + 240, 230, offsetZ + 52),
                                candidate -> candidate.isAlive()
                                        && candidate.getPersistentData().getLong(
                                                "blade_tetra_challenge") == id)
                        .stream().findFirst().orElse(null);
            }
            if (mikage != null) {
                bossEntityId = mikage.getUUID();
                lastBossHealthFraction = Mth.clamp(
                        mikage.getHealth() / Math.max(1.0F, mikage.getMaxHealth()),
                        0.01F, 1.0F);
                bossMissingTicks = 0;
                return;
            }
            if (++bossMissingTicks < 40) {
                if (bossMissingTicks == 1) {
                    LOGGER.warn("Challenge {} lost tracked Mikage {} at health fraction {}",
                            id, bossEntityId, lastBossHealthFraction);
                }
                return;
            }

            // A challenge must never remain in a boss-spawned state with no boss.
            // Recreate the combatant at the last observed health instead of restarting
            // the encounter or silently leaving the party in an empty arena.
            MikageEntity replacement = ModEntities.MIKAGE.get().create(mirror);
            if (replacement == null) {
                bossMissingTicks = 20;
                return;
            }
            replacement.moveTo(offsetX + 179.5D, 64.0D, offsetZ - 10.0D,
                    -90.0F, 0.0F);
            List<ServerPlayer> party = participants.stream()
                    .map(uuid -> mirror.getServer().getPlayerList().getPlayer(uuid))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            replacement.configureForParty(party, reminiscence, id);
            replacement.setHealth(replacement.getMaxHealth()
                    * Mth.clamp(lastBossHealthFraction, 0.01F, 1.0F));
            replacement.prepareOpening(20);
            replacement.restrictTo(new BlockPos(offsetX + 179, 64, offsetZ - 10), 64);
            if (mirror.addFreshEntity(replacement)) {
                bossEntityId = replacement.getUUID();
                bossMissingTicks = 0;
                LOGGER.warn("Challenge {} restored missing Mikage as {} at health fraction {}",
                        id, bossEntityId, lastBossHealthFraction);
            } else {
                bossMissingTicks = 20;
            }
        }

        void tickVisitor(MinecraftServer server, ServerLevel mirror, ServerLevel easter) {
            prepareEasterRoom(easter);
            if (!bossSpawned) {
                mirror.getEntitiesOfClass(MikageEntity.class,
                                new AABB(offsetX - 32, 50, offsetZ - 172,
                                        offsetX + 290, 230, offsetZ + 152))
                        .forEach(MikageEntity::discard);
                MikageEntity mikage = ModEntities.MIKAGE.get().create(mirror);
                if (mikage != null) {
                    mikage.moveTo(offsetX + 172.5D, 64.0D, offsetZ - 2.5D,
                            -90.0F, 0.0F);
                    mikage.configureVisitor(id);
                    mirror.addFreshEntity(mikage);
                    bossSpawned = true;
                }
            }
            forEachPlayer(server, player -> {
                if (visitorArrivals.add(player.getUUID())) {
                    player.teleportTo(mirror, offsetX + 163.5D, 64.0D,
                            offsetZ - 2.5D, -90.0F, 0.0F);
                    player.sendSystemMessage(Component.translatable(
                            "message.blade_tetra.visitor.arrived"));
                }
            });
            renderReturn(mirror, offsetX + 158.5D, offsetZ - 2.5D);
            forEachPlayer(server, player -> {
                if (!easterRoomPlayers.contains(player.getUUID())
                        && player.position().distanceToSqr(
                        offsetX + 158.5D, 64.0D, offsetZ - 2.5D) < 6.25D) {
                    returnPlayer(player);
                }
            });
            if (easterBuilt) {
                renderReturn(easter, easterOriginX() + 156.5D,
                        easterOriginZ() + 114.5D);
                forEachEasterPlayer(server, player -> {
                    if (player.position().distanceToSqr(
                            easterOriginX() + 156.5D, 64.0D,
                            easterOriginZ() + 114.5D) < 6.25D) {
                        returnPlayer(player);
                    }
                });
            }
        }

        void prepareEasterRoom(ServerLevel mirror) {
            if (!easterBuilt && hasCompletedEasterRoom(mirror)) {
                easterBuilt = true;
                easterBuildIndex = EasterRoomData.entries().size();
            }
            if (easterBuilt) {
                return;
            }
            List<EasterRoomData.Entry> blocks = EasterRoomData.entries();
            int limit = Math.min(blocks.size(), easterBuildIndex
                    + GameplayConfig.MIRROR_ARENA_BLOCKS_PER_TICK.get());
            for (; easterBuildIndex < limit; easterBuildIndex++) {
                EasterRoomData.Entry entry = blocks.get(easterBuildIndex);
                mirror.setBlock(new BlockPos(easterOriginX() + entry.x(),
                        63 + entry.y(), easterOriginZ() + entry.z()),
                        entry.state(), 2 | 16);
            }
            if (easterBuildIndex < blocks.size()) {
                return;
            }
            loadEasterRoomBlockEntities(mirror);
            ensureEasterRoomEntities(mirror);
            markEasterRoomComplete(mirror);
            easterBuilt = true;
        }

        void ensureEasterRoomEntities(ServerLevel mirror) {
            if (!hasCompletedEasterEntities(mirror)) {
                loadEasterRoomEntities(mirror, false);
                markEasterEntitiesComplete(mirror);
            }
            if (!hasCompletedEasterTexts(mirror)) {
                loadEasterRoomEntities(mirror, true);
                markEasterTextsComplete(mirror);
            }
        }

        void loadEasterRoomEntities(ServerLevel mirror, boolean texts) {
            for (var source : EasterRoomData.entities()) {
                boolean text = source.getString("id").equals("minecraft:text_display");
                if (text != texts) {
                    continue;
                }
                var tag = source.copy();
                ListTag relativePos = tag.getList("Pos", Tag.TAG_DOUBLE);
                if (relativePos.size() < 3) {
                    continue;
                }
                ListTag absolutePos = new ListTag();
                absolutePos.add(DoubleTag.valueOf(
                        easterOriginX() + relativePos.getDouble(0)));
                absolutePos.add(DoubleTag.valueOf(63.0D + relativePos.getDouble(1)));
                absolutePos.add(DoubleTag.valueOf(
                        easterOriginZ() + relativePos.getDouble(2)));
                tag.put("Pos", absolutePos);
                if (tag.contains("TileX", Tag.TAG_ANY_NUMERIC)) {
                    tag.putInt("TileX", easterOriginX() + tag.getInt("TileX"));
                }
                if (tag.contains("TileY", Tag.TAG_ANY_NUMERIC)) {
                    tag.putInt("TileY", 63 + tag.getInt("TileY"));
                }
                if (tag.contains("TileZ", Tag.TAG_ANY_NUMERIC)) {
                    tag.putInt("TileZ", easterOriginZ() + tag.getInt("TileZ"));
                }
                Entity loaded = EntityType.loadEntityRecursive(
                        tag, mirror, entity -> entity);
                if (loaded != null) {
                    mirror.addFreshEntityWithPassengers(loaded);
                }
            }
        }

        void loadEasterRoomBlockEntities(ServerLevel mirror) {
            for (EasterRoomData.BlockEntityEntry entry :
                    EasterRoomData.blockEntities()) {
                BlockPos pos = new BlockPos(easterOriginX() + entry.x(),
                        63 + entry.y(), easterOriginZ() + entry.z());
                BlockEntity blockEntity = mirror.getBlockEntity(pos);
                if (blockEntity == null) {
                    continue;
                }
                var tag = entry.tag().copy();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                blockEntity.load(tag);
                blockEntity.setChanged();
                mirror.sendBlockUpdated(pos, mirror.getBlockState(pos),
                        mirror.getBlockState(pos), 3);
            }
        }

        boolean hasCompletedEasterRoom(ServerLevel mirror) {
            return mirror.getBlockState(easterCompletionMarker(mirror)).is(Blocks.BEDROCK)
                    && mirror.getBlockState(easterPaletteMarker(mirror))
                            .is(Blocks.CHERRY_PLANKS);
        }

        void markEasterRoomComplete(ServerLevel mirror) {
            mirror.setBlock(easterCompletionMarker(mirror),
                    Blocks.BEDROCK.defaultBlockState(), 2 | 16);
            mirror.setBlock(easterPaletteMarker(mirror),
                    Blocks.CHERRY_PLANKS.defaultBlockState(), 2 | 16);
        }

        BlockPos easterCompletionMarker(ServerLevel mirror) {
            return new BlockPos(easterOriginX(),
                    mirror.getMinBuildHeight() + 1, easterOriginZ());
        }

        BlockPos easterPaletteMarker(ServerLevel mirror) {
            return new BlockPos(easterOriginX(),
                    mirror.getMinBuildHeight() + 2, easterOriginZ());
        }

        boolean hasCompletedEasterEntities(ServerLevel mirror) {
            return mirror.getBlockState(easterEntityMarker(mirror))
                    .is(Blocks.CRYING_OBSIDIAN);
        }

        void markEasterEntitiesComplete(ServerLevel mirror) {
            mirror.setBlock(easterEntityMarker(mirror),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2 | 16);
        }

        BlockPos easterEntityMarker(ServerLevel mirror) {
            return new BlockPos(easterOriginX(),
                    mirror.getMinBuildHeight() + 3, easterOriginZ());
        }

        boolean hasCompletedEasterTexts(ServerLevel mirror) {
            return mirror.getBlockState(easterTextMarker(mirror))
                    .is(Blocks.AMETHYST_BLOCK);
        }

        void markEasterTextsComplete(ServerLevel mirror) {
            mirror.setBlock(easterTextMarker(mirror),
                    Blocks.AMETHYST_BLOCK.defaultBlockState(), 2 | 16);
        }

        BlockPos easterTextMarker(ServerLevel mirror) {
            return new BlockPos(easterOriginX(),
                    mirror.getMinBuildHeight() + 4, easterOriginZ());
        }

        int easterOriginX() {
            return offsetX + 430;
        }

        int easterOriginZ() {
            return offsetZ - 110;
        }

        void teleportToEasterEntrance(ServerPlayer player, ServerLevel mirror) {
            player.teleportTo(mirror, easterOriginX() + 128.5D, 64.0D,
                    easterOriginZ() + 114.5D, -90.0F, 0.0F);
        }

        void enterEasterRoom(ServerPlayer player) {
            ServerLevel easter = player.getServer().getLevel(EASTER_REALM);
            if (!player.level().dimension().equals(MIRROR_REALM)
                    || easter == null || !easterBuilt) {
                player.sendSystemMessage(Component.translatable(
                        "message.blade_tetra.easter_room.preparing"));
                return;
            }
            easterRoomPlayers.add(player.getUUID());
            player.getPersistentData().putBoolean(EASTER_ENTRY_TICKET, true);
            visitorDialogueNodes.remove(player.getUUID());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MikageVisitorDialoguePacket("__close", "", "", "",
                            List.of()));
            teleportToEasterEntrance(player, easter);
            ensureEasterRoomEntities(easter);
            player.sendSystemMessage(Component.translatable(
                    "message.blade_tetra.easter_room.arrived"));
        }

        void openVisitorDialogue(ServerPlayer player) {
            var persisted = player.getPersistentData().getCompound(
                    net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            sendVisitorNode(player, persisted.getBoolean(
                    "blade_tetra_mikage_visitor_intro_seen") ? "topics" : "intro1");
        }

        void handleVisitorChoice(ServerPlayer player, String choice) {
            String current = visitorDialogueNodes.get(player.getUUID());
            if (current == null) {
                return;
            }
            if (current.equals("topics") && choice.equals("easter_room")) {
                enterEasterRoom(player);
                return;
            }
            String next = switch (current) {
                case "intro1" -> choice.equals("next") ? "intro2" : null;
                case "intro2" -> choice.equals("next") ? "intro3" : null;
                case "intro3" -> choice.equals("next") ? "intro4" : null;
                case "intro4" -> choice.equals("next") ? "topics" : null;
                case "echo1" -> choice.equals("next") ? "echo2" : null;
                case "mask1" -> choice.equals("next") ? "mask2" : null;
                case "akatsuki1" -> choice.equals("next") ? "akatsuki2" : null;
                case "akatsuki2" -> choice.equals("next") ? "akatsuki3" : null;
                case "akatsuki3" -> choice.equals("next") ? "akatsuki4" : null;
                case "akatsuki4" -> choice.equals("next") ? "akatsuki5" : null;
                case "akatsuki5" -> choice.equals("next") ? "akatsuki6" : null;
                case "mikage1" -> choice.equals("next") ? "mikage2" : null;
                case "mikage2" -> choice.equals("next") ? "mikage3" : null;
                case "mikage3" -> choice.equals("next") ? "mikage4" : null;
                case "mikage4" -> choice.equals("next") ? "mikage5" : null;
                case "mikage5" -> choice.equals("next") ? "mikage6" : null;
                case "mikage6" -> choice.equals("next") ? "mikage7" : null;
                case "topics" -> switch (choice) {
                    case "echo" -> "echo1";
                    case "mask" -> "mask1";
                    case "akatsuki" -> "akatsuki1";
                    case "mikage" -> "mikage1";
                    default -> null;
                };
                case "echo2", "mask2", "akatsuki6", "mikage7" ->
                        choice.equals("back") ? "topics" : null;
                default -> null;
            };
            if (next == null) {
                return;
            }
            if (current.equals("intro4")) {
                var data = player.getPersistentData();
                var persisted = data.getCompound(
                        net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                persisted.putBoolean("blade_tetra_mikage_visitor_intro_seen", true);
                data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
            }
            sendVisitorNode(player, next);
        }

        void sendVisitorNode(ServerPlayer player, String node) {
            visitorDialogueNodes.put(player.getUUID(), node);
            List<MikageVisitorDialoguePacket.Option> options;
            String expression = "neutral";
            switch (node) {
                case "intro1" -> options = List.of(option("next", "continue"));
                case "intro2" -> {
                    expression = "soft";
                    options = List.of(option("next", "continue"));
                }
                case "intro3" -> options = List.of(option("next", "continue"));
                case "intro4" -> options = List.of(option("next", "continue"));
                case "topics" -> options = List.of(
                        option("akatsuki", "akatsuki"), option("mikage", "mikage"),
                        option("echo", "echo"), option("mask", "mask"),
                        option("easter_room", "easter_room"));
                case "echo1" -> {
                    expression = "distant";
                    options = List.of(option("next", "continue"));
                }
                case "echo2" -> options = List.of(option("back", "back"));
                case "mask1" -> options = List.of(option("next", "continue"));
                case "mask2" -> {
                    expression = "soft";
                    options = List.of(option("back", "back"));
                }
                case "akatsuki1" -> {
                    expression = "soft";
                    options = List.of(option("next", "continue"));
                }
                case "akatsuki2" -> {
                    expression = "distant";
                    options = List.of(option("next", "continue"));
                }
                case "akatsuki3" -> {
                    expression = "soft";
                    options = List.of(option("next", "continue"));
                }
                case "akatsuki4" -> {
                    expression = "distant";
                    options = List.of(option("next", "continue"));
                }
                case "akatsuki5" -> {
                    expression = "serious";
                    options = List.of(option("next", "continue"));
                }
                case "akatsuki6" ->
                    options = List.of(option("back", "back"));
                case "mikage1" -> options = List.of(option("next", "continue"));
                case "mikage2" -> options = List.of(option("next", "continue"));
                case "mikage3", "mikage4" -> {
                    expression = "distant";
                    options = List.of(option("next", "continue"));
                }
                case "mikage5" -> {
                    expression = "soft";
                    options = List.of(option("next", "continue"));
                }
                case "mikage6" -> options = List.of(option("next", "continue"));
                case "mikage7" -> {
                    expression = "soft";
                    options = List.of(option("back", "back"));
                }
                default -> options = List.of(option("back", "back"));
            }
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MikageVisitorDialoguePacket(node,
                            "dialogue.blade_tetra.mikage.visitor." + node,
                            "", expression, options));
        }

        MikageVisitorDialoguePacket.Option option(String id, String label) {
            return new MikageVisitorDialoguePacket.Option(id,
                    "screen.blade_tetra.mikage_visitor.option." + label);
        }

        void victory(MinecraftServer server) {
            if (won) {
                return;
            }
            won = true;
            MikageDialogue victoryLine = reminiscence
                    ? MikageDialogue.REMINISCENCE_VICTORY : MikageDialogue.VICTORY;
            victoryTeleportTicks = victoryLine.holdTicks() + 10;
            dialogueQueue.clear();
            speakNow(server, victoryLine);
            forEachPlayer(server, player -> {
                stopMusic(player);
                hideBoundary(player);
                hideHud(player);
                giveOrDrop(player, ChallengeRewards.remnant());
                var playerData = player.getPersistentData();
                var persisted = playerData.getCompound(
                        net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                if (!persisted.getBoolean("blade_tetra_mikage_cleared")) {
                    giveOrDrop(player, ChallengeRewards.boundaryScroll());
                    giveOrDrop(player, ChallengeRewards.mask());
                    persisted.putBoolean("blade_tetra_mikage_cleared", true);
                    playerData.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,
                            persisted);
                }
                if (reminiscence) {
                    ItemStack mask = null;
                    for (ItemStack stack : player.getAllSlots()) {
                        if (stack.is(dev.bladetetra.registry.ModItems.BROKEN_ONI_MASK.get())) {
                            mask = stack;
                            break;
                        }
                    }
                    if (mask == null) {
                        mask = ChallengeRewards.mask();
                        mask.getOrCreateTag().putBoolean("blade_tetra_mikage_echo", true);
                        giveOrDrop(player, mask);
                    } else {
                        mask.getOrCreateTag().putBoolean("blade_tetra_mikage_echo", true);
                    }
                    player.sendSystemMessage(Component.translatable(
                            "message.blade_tetra.mask.echo_awakened"));
                }
            });
        }

        void renderReturn(ServerLevel mirror) {
            renderReturn(mirror, offsetX - 4.5D, offsetZ + 0.5D);
        }

        void renderReturn(ServerLevel mirror, double x, double z) {
            if (mirror.getGameTime() % 2L == 0L) {
                Vec3 center = new Vec3(x, 64.1D, z);
                ToriiParticles.render(mirror, center, new Vec3(0.0D, 0.0D, 1.0D),
                        false, 1.0D);
                mirror.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.2D,
                        center.z, 4, 0.7D, 1.0D, 0.7D, 0.0D);
            }
        }

        void renderVermilionPath(ServerLevel mirror) {
            if (mirror.getGameTime() % 4L != 0L) {
                return;
            }
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(0.72F, 0.035F, 0.08F), 0.72F);
            int phase = (int) (mirror.getGameTime() / 4L % 8L);
            for (int i = phase; i <= 28; i += 8) {
                double t = i / 28.0D;
                mirror.sendParticles(dust,
                        offsetX + 22.5D + 137.0D * t,
                        64.08D,
                        offsetZ + 0.5D - 3.0D * t,
                        1, 0, 0, 0, 0);
            }
        }

        void returnPlayer(ServerPlayer player) {
            stopMusic(player);
            hideBoundary(player);
            hideHud(player);
            Origin origin = origins.remove(player.getUUID());
            participants.remove(player.getUUID());
            disconnectedTicks.remove(player.getUUID());
            visitorArrivals.remove(player.getUUID());
            easterRoomPlayers.remove(player.getUUID());
            visitorDialogueNodes.remove(player.getUUID());
            if (origin == null) {
                returnFromPersistentOrigin(player);
                return;
            }
            ServerLevel destination = player.getServer().getLevel(origin.dimension);
            if (destination == null) {
                destination = player.getServer().overworld();
            }
            player.teleportTo(destination, origin.position.x, origin.position.y,
                    origin.position.z, origin.yaw, origin.pitch);
            clearPersistentChallenge(player, destination.getGameTime());
        }

        void defeatPlayer(ServerPlayer player) {
            MikageDialogue[] lines = {
                    MikageDialogue.DEFEAT_1,
                    MikageDialogue.DEFEAT_2,
                    MikageDialogue.DEFEAT_3
            };
            // Dimension changes stop active client sounds. Queue the defeat VO until the
            // destination level has settled, while keeping its text visible immediately.
            sendDialogue(player, lines[player.getRandom().nextInt(lines.length)], 20);
            returnPlayer(player);
        }

        boolean hasParticipant(UUID playerId) {
            return participants.contains(playerId);
        }

        boolean isInEasterRoom(UUID playerId) {
            return participants.contains(playerId) && easterRoomPlayers.contains(playerId);
        }

        void markDisconnected(UUID playerId) {
            if (participants.contains(playerId)) {
                disconnectedTicks.put(playerId, 0);
            }
        }

        void detachForEasterLogout(ServerPlayer player) {
            stopMusic(player);
            hideBoundary(player);
            hideHud(player);
            participants.remove(player.getUUID());
            origins.remove(player.getUUID());
            disconnectedTicks.remove(player.getUUID());
            visitorArrivals.remove(player.getUUID());
            easterRoomPlayers.remove(player.getUUID());
            visitorDialogueNodes.remove(player.getUUID());
        }

        boolean reconnect(UUID playerId) {
            if (!participants.contains(playerId)) {
                return false;
            }
            disconnectedTicks.remove(playerId);
            return true;
        }

        void tickDisconnected() {
            Iterator<Map.Entry<UUID, Integer>> iterator = disconnectedTicks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                int ticks = entry.getValue() + 1;
                if (ticks > RECONNECT_GRACE_TICKS) {
                    participants.remove(entry.getKey());
                    origins.remove(entry.getKey());
                    iterator.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }

        void forfeitOutside(ServerPlayer player) {
            stopMusic(player);
            hideBoundary(player);
            hideHud(player);
            participants.remove(player.getUUID());
            origins.remove(player.getUUID());
            disconnectedTicks.remove(player.getUUID());
            easterRoomPlayers.remove(player.getUUID());
            clearPersistentChallenge(player, player.level().getGameTime());
        }

        void queueDialogue(MikageDialogue line) {
            if (!dialogueQueue.contains(line)) {
                dialogueQueue.addLast(line);
            }
        }

        boolean tryDialogue(MinecraftServer server, MikageDialogue line) {
            if (dialogueTicks > 0 || !dialogueQueue.isEmpty()) {
                return false;
            }
            speakNow(server, line);
            return true;
        }

        boolean tryVoice(MinecraftServer server, MikageDialogue line) {
            if (dialogueTicks > 0 || !dialogueQueue.isEmpty()) {
                return false;
            }
            forEachPlayer(server, player -> ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new MikageDialoguePacket("", line.voiceEvent(),
                            line.holdTicks(), 0)));
            dialogueTicks = line.holdTicks();
            return true;
        }

        void tickDialogue(MinecraftServer server) {
            if (dialogueTicks > 0) {
                dialogueTicks--;
            }
            if (dialogueTicks <= 0 && !dialogueQueue.isEmpty()) {
                speakNow(server, dialogueQueue.removeFirst());
            }
        }

        void speakNow(MinecraftServer server, MikageDialogue line) {
            forEachPlayer(server, player -> sendDialogue(player, line));
            dialogueTicks = line.holdTicks();
        }

        void sendDialogue(ServerPlayer player, MikageDialogue line) {
            sendDialogue(player, line, 0);
        }

        void sendDialogue(ServerPlayer player, MikageDialogue line, int delayTicks) {
            player.sendSystemMessage(Component.translatable(line.translationKey()));
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MikageDialoguePacket(line.translationKey(), line.voiceEvent(),
                            line.holdTicks(), delayTicks));
        }

        void broadcast(MinecraftServer server, Component message) {
            if (server == null) {
                return;
            }
            forEachPlayer(server, player -> player.sendSystemMessage(message));
        }

        boolean hasAwakenedAkatsuki(MinecraftServer server) {
            final boolean[] found = {false};
            forEachPlayer(server, player -> {
                for (ItemStack stack : player.getAllSlots()) {
                    if (stack.getOrCreateTag().getBoolean("blade_tetra_akatsuki_unlocked")) {
                        found[0] = true;
                    }
                }
            });
            return found[0];
        }

        void forEachPlayer(MinecraftServer server,
                java.util.function.Consumer<ServerPlayer> action) {
            for (UUID uuid : Set.copyOf(participants)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(MIRROR_REALM)) {
                    action.accept(player);
                }
            }
        }

        void forEachEasterPlayer(MinecraftServer server,
                java.util.function.Consumer<ServerPlayer> action) {
            for (UUID uuid : Set.copyOf(easterRoomPlayers)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(EASTER_REALM)) {
                    action.accept(player);
                }
            }
        }

        boolean anyPlayer(MinecraftServer server,
                java.util.function.Predicate<ServerPlayer> predicate) {
            for (UUID uuid : participants) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(MIRROR_REALM)
                        && predicate.test(player)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Origin(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
    }

    private ChallengeManager() {
    }
}
