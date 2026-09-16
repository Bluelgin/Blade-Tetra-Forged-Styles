package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.MikageVisitorDialoguePacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative Mikage postgame 神域 ritual. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineDomainManager {
    public static final ResourceKey<Level> DIVINE_REALM = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            new ResourceLocation(BladeTetra.MOD_ID, "divine_domain"));

    private static final String DIVINE_CHALLENGE = "blade_tetra_divine_challenge";
    private static final String DIVINE_ORIGIN_X = "blade_tetra_divine_origin_x";
    private static final String DIVINE_ORIGIN_Z = "blade_tetra_divine_origin_z";
    private static final Map<Long, Session> SESSIONS = new HashMap<>();

    public static void showLore(ServerPlayer player) {
        MikageEntity mikage = visitorGuide(player);
        if (mikage == null) {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MikageVisitorDialoguePacket("divine_lore",
                        "我守的从来不是这间屋子。界门之后，是安置刀下之魂的神域。如今那里只剩污染。门后的东西不认识你——它们认识的，是你手中的刀。",
                        "", "serious", List.of(
                        new MikageVisitorDialoguePacket.Option("divine_enter", "进入神域"),
                        new MikageVisitorDialoguePacket.Option("divine_back", "返回"))));
    }

    public static void backToTopics(ServerPlayer player) {
        MikageEntity mikage = visitorGuide(player);
        if (mikage != null) {
            ChallengeManager.openVisitorDialogue(player, mikage);
        }
    }

    public static void tryEnterFromVisitor(ServerPlayer player) {
        if (!player.level().dimension().equals(ChallengeManager.MIRROR_REALM)
                || !player.getPersistentData().contains(ChallengeManager.PLAYER_CHALLENGE)) {
            return;
        }
        MikageEntity mikage = visitorGuide(player);
        if (mikage == null) {
            player.sendSystemMessage(Component.literal("界门没有回应。"));
            return;
        }
        var persisted = player.getPersistentData().getCompound(
                net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean("blade_tetra_mikage_cleared")) {
            player.sendSystemMessage(Component.literal("你还没有得到御影的认可。"));
            return;
        }
        ServerLevel divine = player.getServer().getLevel(DIVINE_REALM);
        if (divine == null) {
            player.sendSystemMessage(Component.literal("神域尚未加载。"));
            return;
        }
        long challengeId = player.getPersistentData().getLong(ChallengeManager.PLAYER_CHALLENGE);
        int ox = Mth.floor(mikage.getX() - 172.5D);
        int oz = Mth.floor(mikage.getZ() + 2.5D);
        Session session = SESSIONS.computeIfAbsent(challengeId,
                ignored -> new Session(challengeId, ox, oz));
        session.players.add(player.getUUID());
        DivineDomainArenaData.build(divine, ox, oz);

        player.getPersistentData().putLong(DIVINE_CHALLENGE, challengeId);
        player.getPersistentData().putInt(DIVINE_ORIGIN_X, ox);
        player.getPersistentData().putInt(DIVINE_ORIGIN_Z, oz);
        // Deliberately detach the visitor challenge tag during the dimension hop.
        // ChallengeManager therefore keeps the visitor session alive instead of
        // treating the intentional 神域 transfer as a forfeit.
        player.getPersistentData().remove(ChallengeManager.PLAYER_CHALLENGE);
        closeDialogue(player);
        BlockPos entry = DivineDomainArenaData.entry(ox, oz);
        player.teleportTo(divine, entry.getX() + 0.5D, entry.getY() + 0.1D,
                entry.getZ() + 0.5D, 180.0F, 0.0F);
        player.sendSystemMessage(Component.literal("神域残响：空手取下挂刀台上的木偶。")
                .withStyle(ChatFormatting.DARK_RED));
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel divine = event.getServer().getLevel(DIVINE_REALM);
        if (divine == null) {
            return;
        }
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            session.tick(event.getServer(), divine);
            if (session.players.isEmpty()) {
                session.cleanup(divine);
                iterator.remove();
            }
        }
    }

    static boolean isWaitingForPuppet(long challengeId) {
        Session session = SESSIONS.get(challengeId);
        return session != null && session.state == State.WAITING_PUPPET;
    }

    static boolean takePuppetFromStand(ServerPlayer player) {
        if (!player.level().dimension().equals(DIVINE_REALM)
                || !player.getPersistentData().contains(DIVINE_CHALLENGE)
                || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        long id = player.getPersistentData().getLong(DIVINE_CHALLENGE);
        Session session = SESSIONS.get(id);
        if (session == null || session.state != State.WAITING_PUPPET
                || !session.players.contains(player.getUUID())) {
            return false;
        }

        long kills = totalSlashBladeKills(player);
        session.kills = kills;
        session.tier = DivineDomainTier.fromKills(kills);
        session.owner = player.getUUID();
        session.state = State.ARMED;

        player.setItemInHand(InteractionHand.MAIN_HAND,
                KarmicPuppetItem.snapshot(kills, session.tier, id));
        player.sendSystemMessage(Component.literal("木偶记录了 " + kills + " 个刀下亡魂 · "
                + session.tier.displayName()).withStyle(ChatFormatting.DARK_RED));
        player.sendSystemMessage(Component.literal("把杀业木偶投入右侧祭火，仪式才会开始。")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    @SubscribeEvent
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DIVINE_REALM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DIVINE_REALM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void protectExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(DIVINE_REALM)) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static MikageEntity visitorGuide(ServerPlayer player) {
        if (!player.level().dimension().equals(ChallengeManager.MIRROR_REALM)) {
            return null;
        }
        long id = player.getPersistentData().getLong(ChallengeManager.PLAYER_CHALLENGE);
        return player.level().getEntitiesOfClass(MikageEntity.class,
                        player.getBoundingBox().inflate(16.0D),
                        mikage -> mikage.isVisitorGuide()
                                && mikage.getPersistentData().getLong("blade_tetra_challenge") == id)
                .stream().findFirst().orElse(null);
    }

    private static long totalSlashBladeKills(ServerPlayer player) {
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            int kills = stack.getCapability(ItemSlashBlade.BLADESTATE)
                    .map(state -> Math.max(0, state.getKillCount())).orElse(0);
            if (Long.MAX_VALUE - total < kills) {
                return Long.MAX_VALUE;
            }
            total += kills;
        }
        return total;
    }

    private static void closeDialogue(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MikageVisitorDialoguePacket("__close", "", "", "", List.of()));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private enum State { WAITING_PUPPET, ARMED, IGNITING, ACTIVE, CLEARED }

    private static final class Session {
        final long id;
        final int originX;
        final int originZ;
        final Set<UUID> players = new HashSet<>();
        final Set<UUID> enemies = new HashSet<>();
        State state = State.WAITING_PUPPET;
        UUID owner;
        long kills;
        DivineDomainTier tier = DivineDomainTier.ECHO;
        int ignitionTicks;
        int wave;
        int nextWaveTicks;
        int pressureTicks;
        boolean rewarded;

        Session(long id, int originX, int originZ) {
            this.id = id;
            this.originX = originX;
            this.originZ = originZ;
        }

        void tick(MinecraftServer server, ServerLevel level) {
            players.removeIf(uuid -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                return player == null || !player.level().dimension().equals(DIVINE_REALM);
            });
            if (players.isEmpty()) {
                return;
            }
            for (UUID uuid : Set.copyOf(players)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;
                if (!DivineDomainArenaData.contains(player.getX(), player.getZ(), originX, originZ)
                        || player.getY() < DivineDomainArenaData.FLOOR_Y - 10) {
                    BlockPos entry = DivineDomainArenaData.entry(originX, originZ);
                    player.teleportTo(level, entry.getX() + 0.5D, entry.getY() + 0.1D,
                            entry.getZ() + 0.5D, 180.0F, 0.0F);
                }
            }
            renderAtmosphere(level);
            switch (state) {
                case WAITING_PUPPET -> { }
                case ARMED -> detectRitualPuppet(level);
                case IGNITING -> tickIgnition(level);
                case ACTIVE -> tickEncounter(server, level);
                case CLEARED -> tickCleared(server, level);
            }
        }

        void detectRitualPuppet(ServerLevel level) {
            BlockPos fire = DivineDomainArenaData.fire(originX, originZ);
            AABB box = new AABB(fire).inflate(1.7D, 1.5D, 1.7D);
            ItemEntity offered = level.getEntitiesOfClass(ItemEntity.class, box,
                    item -> KarmicPuppetItem.hasSnapshot(item.getItem())
                            && item.getItem().getOrCreateTag().getLong(
                                    KarmicPuppetItem.TAG_SESSION) == id)
                    .stream().findFirst().orElse(null);
            if (offered == null) return;
            offered.discard();
            state = State.IGNITING;
            ignitionTicks = 50;
            level.playSound(null, fire, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 1.0F, 0.55F);
            broadcast(level.getServer(), Component.literal("祭火接受了杀业木偶。杀业正在回响……")
                    .withStyle(ChatFormatting.DARK_RED));
        }

        void tickIgnition(ServerLevel level) {
            BlockPos altar = DivineDomainArenaData.altar(originX, originZ);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, altar.getX() + 0.5D,
                    altar.getY() + 1.2D, altar.getZ() + 0.5D, 12,
                    2.0D, 0.8D, 2.0D, 0.02D);
            if (--ignitionTicks > 0) return;
            state = State.ACTIVE;
            nextWaveTicks = 20;
            pressureTicks = tier.pressureIntervalTicks();
            broadcast(level.getServer(), Component.literal("神域仪式开始 · " + tier.displayName())
                    .withStyle(ChatFormatting.RED));
        }

        void tickEncounter(MinecraftServer server, ServerLevel level) {
            enemies.removeIf(uuid -> {
                Entity entity = level.getEntity(uuid);
                return !(entity instanceof LivingEntity living) || !living.isAlive();
            });
            if (!enemies.isEmpty()) {
                if (tier.pressureIntervalTicks() > 0 && --pressureTicks <= 0
                        && enemies.size() < tier.concurrentForWave(Math.max(1, wave)) + 3) {
                    spawnOne(level, wave, wave + pressureTicks + enemies.size(), false);
                    pressureTicks = tier.pressureIntervalTicks();
                }
                return;
            }
            if (wave >= tier.waves()) {
                clear(server, level);
                return;
            }
            if (nextWaveTicks-- > 0) return;
            wave++;
            int count = tier.concurrentForWave(wave);
            for (int i = 0; i < count; i++) {
                boolean forcedElite = wave == tier.waves() && i == count - 1
                        && tier.grantsDeadThoughtSeal();
                spawnOne(level, wave, i, forcedElite);
            }
            nextWaveTicks = 60;
            pressureTicks = tier.pressureIntervalTicks();
            broadcast(server, Component.literal("第 " + wave + " / " + tier.waves() + " 波")
                    .withStyle(ChatFormatting.GRAY));
        }

        void spawnOne(ServerLevel level, int waveIndex, int index, boolean forcedElite) {
            EntityType<? extends Mob> type = selectType(waveIndex, index);
            Mob mob = type.create(level);
            if (mob == null) return;
            BlockPos pos = DivineDomainArenaData.spawnPoint(originX, originZ,
                    waveIndex * 17 + index, tier.spawnDirections());
            mob.moveTo(pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            mob.setPersistenceRequired();
            mob.getPersistentData().putLong(DIVINE_CHALLENGE, id);
            boolean elite = forcedElite || level.random.nextDouble() < tier.eliteChance();
            if (elite) {
                mob.setCustomName(Component.literal(forcedElite ? "刀下众生" : "染业亡魂")
                        .withStyle(ChatFormatting.DARK_RED));
                mob.setCustomNameVisible(forcedElite);
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 0, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 0, false, true));
                if (tier.ordinal() >= DivineDomainTier.ASURA.ordinal()) {
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 0, false, true));
                }
            }
            ServerPlayer target = owner == null ? null
                    : level.getServer().getPlayerList().getPlayer(owner);
            if (target != null && target.level() == level) {
                mob.setTarget(target);
            }
            if (level.addFreshEntity(mob)) {
                enemies.add(mob.getUUID());
            }
        }

        EntityType<? extends Mob> selectType(int waveIndex, int index) {
            int selector = Math.floorMod(waveIndex * 3 + index, 7);
            if (tier == DivineDomainTier.ECHO) {
                return selector % 2 == 0 ? EntityType.HUSK : EntityType.STRAY;
            }
            if (tier == DivineDomainTier.GRUDGE) {
                return selector < 3 ? EntityType.HUSK : selector < 6 ? EntityType.STRAY : EntityType.VEX;
            }
            if (tier == DivineDomainTier.HUNDRED_GHOSTS) {
                return selector < 2 ? EntityType.VEX : selector < 5 ? EntityType.WITHER_SKELETON : EntityType.STRAY;
            }
            if (tier == DivineDomainTier.ASURA) {
                return selector < 3 ? EntityType.WITHER_SKELETON : selector < 5 ? EntityType.VEX : EntityType.PIGLIN_BRUTE;
            }
            return selector < 3 ? EntityType.VEX : selector < 6 ? EntityType.WITHER_SKELETON : EntityType.PIGLIN_BRUTE;
        }

        void clear(MinecraftServer server, ServerLevel level) {
            state = State.CLEARED;
            if (!rewarded) {
                rewarded = true;
                for (UUID uuid : Set.copyOf(players)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null || player.level() != level) continue;
                    ItemStack remnants = new ItemStack(ModItems.SWORD_GHOST_REMNANT.get(),
                            1 + tier.ordinal());
                    giveOrDrop(player, remnants);
                    player.giveExperiencePoints(15 + tier.ordinal() * 12);
                    if (tier.grantsDeadThoughtSeal()) {
                        var data = player.getPersistentData();
                        var persisted = data.getCompound(
                                net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                        if (!persisted.getBoolean("blade_tetra_dead_thought_divine_seal")) {
                            giveOrDrop(player, new ItemStack(ModItems.DEAD_THOUGHT_SOUL_SEAL.get()));
                            persisted.putBoolean("blade_tetra_dead_thought_divine_seal", true);
                            data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
                        }
                    }
                }
            }
            level.playSound(null, DivineDomainArenaData.altar(originX, originZ),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.35F);
            broadcast(server, Component.literal("亡魂归于寂静。你可以停留；回到界门并潜行即可撤离。")
                    .withStyle(ChatFormatting.GOLD));
        }

        void tickCleared(MinecraftServer server, ServerLevel level) {
            BlockPos entry = DivineDomainArenaData.entry(originX, originZ);
            if (level.getGameTime() % 2L == 0L) {
                level.sendParticles(ParticleTypes.ENCHANT, entry.getX() + 0.5D,
                        entry.getY() + 1.1D, entry.getZ() + 0.5D, 5,
                        0.8D, 1.0D, 0.8D, 0.0D);
            }
            for (UUID uuid : Set.copyOf(players)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null || player.level() != level) continue;
                if (player.isCrouching() && player.position().distanceToSqr(
                        entry.getX() + 0.5D, entry.getY(), entry.getZ() + 0.5D) < 9.0D) {
                    returnToVisitor(player);
                }
            }
        }

        void returnToVisitor(ServerPlayer player) {
            ServerLevel mirror = player.getServer().getLevel(ChallengeManager.MIRROR_REALM);
            if (mirror == null) return;
            players.remove(player.getUUID());
            player.getPersistentData().putLong(ChallengeManager.PLAYER_CHALLENGE, id);
            player.getPersistentData().remove(DIVINE_CHALLENGE);
            player.teleportTo(mirror, originX + 163.5D, 64.0D, originZ - 2.5D,
                    -90.0F, 0.0F);
            player.sendSystemMessage(Component.literal("你回到了御影守候的界门前。")
                    .withStyle(ChatFormatting.GRAY));
        }

        void renderAtmosphere(ServerLevel level) {
            if (level.getGameTime() % 6L != 0L) return;
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.48F, 0.02F, 0.03F), 0.85F);
            for (int i = 0; i < 8; i++) {
                double angle = (level.getGameTime() * 0.01D) + Math.PI * 2.0D * i / 8.0D;
                double radius = 18.0D + (i % 3) * 9.0D;
                level.sendParticles(dust, originX + 0.5D + Math.cos(angle) * radius,
                        DivineDomainArenaData.FLOOR_Y + 1.2D,
                        originZ + 0.5D + Math.sin(angle) * radius,
                        1, 0.15D, 0.35D, 0.15D, 0.0D);
            }
        }

        void broadcast(MinecraftServer server, Component message) {
            for (UUID uuid : players) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(DIVINE_REALM)) {
                    player.sendSystemMessage(message);
                }
            }
        }

        void cleanup(ServerLevel level) {
            for (UUID uuid : enemies) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) entity.discard();
            }
            enemies.clear();
        }
    }

    private DivineDomainManager() {
    }
}
