package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.RaikiriChainHandler;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.entity.IShootable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-owned low-health execution for awakened Akatsuki. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AkatsukiExecution {
    private static final String BLADE_ID = "blade_tetra_akatsuki_execution_id";
    private static final String COOLDOWN_UNTIL = "blade_tetra_akatsuki_execution_cooldown";
    private static final int RECORD_EXPIRY_TICKS = 1200;
    private static final int STALL_TICKS = 40;
    private static final int MINIMUM_REAL_CUTS = 12;
    private static final double MAXIMUM_DISTANCE_SQUARED = 24.0D * 24.0D;

    private static final ResourceKey<DamageType> EXECUTION_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "akatsuki_execution"));
    private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "akatsuki_execution_immune"));

    private static final Map<CombatKey, CombatRecord> RECORDS = new HashMap<>();
    private static final Map<UUID, Execution> EXECUTIONS = new HashMap<>();
    private static final List<PendingStart> PENDING = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F
                || event.getSource().is(EXECUTION_DAMAGE)
                || BladeTechniqueHandler.isTechniqueDamage(event.getSource())
                || SoulLegacyDamageGuard.isSecondary(event.getSource())
                || target.getType().is(IMMUNE)) {
            return;
        }
        ServerPlayer attacker = resolveAttacker(event);
        if (attacker == null || !canAffect(attacker, target)) return;
        ItemStack blade = attacker.getMainHandItem();
        if (!AkatsukiAwakening.isActive(blade)) return;

        long now = level.getGameTime();
        UUID bladeId = bladeId(blade);
        Execution active = EXECUTIONS.get(target.getUUID());
        if (active != null && active.attackerId.equals(attacker.getUUID())
                && active.bladeId.equals(bladeId)) {
            active.lastManualHit = now;
            return;
        }

        CombatKey key = new CombatKey(attacker.getUUID(), target.getUUID());
        CombatRecord record = RECORDS.computeIfAbsent(key,
                ignored -> new CombatRecord(bladeId, now));
        if (!record.bladeId.equals(bladeId) || now - record.lastHit > RECORD_EXPIRY_TICKS) {
            record = new CombatRecord(bladeId, now);
            RECORDS.put(key, record);
        }
        float effectiveDamage = Math.min(event.getAmount(), target.getHealth());
        record.add(effectiveDamage, now);

        if (record.triggered
                || target.getMaxHealth() < GameplayConfig.AKATSUKI_EXECUTION_MINIMUM_TARGET_HEALTH.get()
                || blade.getOrCreateTag().getLong(COOLDOWN_UNTIL) > now
                || record.totalDamage < target.getMaxHealth()
                        * GameplayConfig.AKATSUKI_REQUIRED_CONTRIBUTION.get()) {
            return;
        }

        double representative = AkatsukiExecutionMath.representativeDamage(
                new ArrayList<>(record.recentDamage));
        float threshold = dynamicThreshold(target, representative);
        float projectedHealth = target.getHealth() - event.getAmount();
        if (projectedHealth > threshold) return;

        record.triggered = true;
        float expectedStartHealth = Math.max(0.05F,
                Math.min(target.getHealth(), Math.max(0.05F, projectedHealth)));
        float pulseDamage = AkatsukiExecutionMath.pulseDamage(representative,
                expectedStartHealth,
                GameplayConfig.AKATSUKI_EXECUTION_DAMAGE_FRACTION.get(),
                MINIMUM_REAL_CUTS);
        PENDING.add(new PendingStart(level.dimension(), attacker.getUUID(),
                target.getUUID(), bladeId, now + 1L, threshold, pulseDamage));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Execution active = EXECUTIONS.get(event.getEntity().getUUID());
        if (active != null) {
            // Death can fire synchronously from target.hurt while the server-tick
            // iterator owns this map. Let that iterator close the timeline safely.
            if (event.getSource().is(EXECUTION_DAMAGE)) return;
            EXECUTIONS.remove(event.getEntity().getUUID());
            finish(level, active, true);
            return;
        }
        PendingStart pending = PENDING.stream()
                .filter(value -> value.targetId.equals(event.getEntity().getUUID()))
                .findFirst().orElse(null);
        if (pending == null) return;
        PENDING.remove(pending);
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(pending.attackerId);
        if (attacker != null) {
            sendVfx(level, attacker, event.getEntity(),
                    BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON,
                    18, 0.82F, level.random.nextInt());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        startPending(server);
        tickExecutions(server);
        if (server.getTickCount() % 200 == 0) cleanupRecords(server);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PENDING.removeIf(value -> value.dimension.equals(level.dimension()));
        EXECUTIONS.values().removeIf(value -> value.dimension.equals(level.dimension()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RECORDS.clear();
        EXECUTIONS.clear();
        PENDING.clear();
    }

    private static void startPending(MinecraftServer server) {
        Iterator<PendingStart> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingStart pending = iterator.next();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null || level.getGameTime() < pending.startAt) continue;
            iterator.remove();
            ServerPlayer attacker = server.getPlayerList().getPlayer(pending.attackerId);
            Entity found = level.getEntity(pending.targetId);
            LivingEntity target = found instanceof LivingEntity living ? living : null;
            if (attacker == null || target == null || !target.isAlive()
                    || EXECUTIONS.containsKey(target.getUUID())
                    || !holdsBlade(attacker, pending.bladeId)
                    || attacker.distanceToSqr(target) > MAXIMUM_DISTANCE_SQUARED
                    || target.getHealth() > pending.threshold * 1.25F) {
                continue;
            }
            long now = level.getGameTime();
            ItemStack blade = attacker.getMainHandItem();
            int petalMarks = SoulResonance.isFormed(blade,
                    SoulResonance.Resonance.FUNERAL_BLOSSOM)
                    ? SenbonzakuraAwakening.consumeMarksForFinalMoon(blade, now) : 0;
            float resonantPulseDamage = pending.pulseDamage
                    * (1.0F + petalMarks * 0.25F);
            Execution execution = new Execution(pending.dimension, pending.attackerId,
                    pending.targetId, pending.bladeId, now, now,
                    now, now, pending.threshold, resonantPulseDamage);
            EXECUTIONS.put(target.getUUID(), execution);
            blade.getOrCreateTag().putLong(COOLDOWN_UNTIL,
                    now + GameplayConfig.AKATSUKI_EXECUTION_COOLDOWN_TICKS.get());
            sendVfx(level, attacker, target,
                    BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON,
                    GameplayConfig.AKATSUKI_EXECUTION_TIMEOUT_TICKS.get(),
                    Mth.clamp(target.getBbHeight() / 2.0F, 0.85F, 2.1F),
                    level.random.nextInt());
            level.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.PLAYERS, 0.75F, 0.62F);
            if (SoulResonance.isFormed(blade,
                    SoulResonance.Resonance.MIRROR_MOON)) {
                KyoukaAwakening.playFinalMoonReflection(level, attacker, target);
            }
            if (petalMarks > 0) {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                        target.getZ(), 18 + petalMarks * 10,
                        0.85D, 0.65D, 0.85D, 0.035D);
                level.playSound(null, target.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.72F, 1.18F);
            }
        }
    }

    private static void tickExecutions(MinecraftServer server) {
        Iterator<Execution> iterator = EXECUTIONS.values().iterator();
        while (iterator.hasNext()) {
            Execution execution = iterator.next();
            ServerLevel level = server.getLevel(execution.dimension);
            ServerPlayer attacker = server.getPlayerList().getPlayer(execution.attackerId);
            Entity found = level == null ? null : level.getEntity(execution.targetId);
            LivingEntity target = found instanceof LivingEntity living ? living : null;
            long now = level == null ? Long.MAX_VALUE : level.getGameTime();

            if (level == null || attacker == null || target == null || !target.isAlive()) {
                if (level != null) finish(level, execution, target != null && !target.isAlive());
                iterator.remove();
                continue;
            }
            boolean invalid = target.getType().is(IMMUNE)
                    || !holdsBlade(attacker, execution.bladeId)
                    || attacker.level() != level
                    || attacker.distanceToSqr(target) > MAXIMUM_DISTANCE_SQUARED
                    || now - execution.lastManualHit > GameplayConfig.AKATSUKI_EXECUTION_MAINTAIN_TICKS.get() + 20L
                    || now - execution.startedAt >= GameplayConfig.AKATSUKI_EXECUTION_TIMEOUT_TICKS.get()
                    || target.getHealth() > execution.threshold * 1.25F;
            if (invalid) {
                finish(level, execution, false);
                iterator.remove();
                continue;
            }

            renderServerAccent(level, target, execution, now);
            if (now - execution.lastManualHit > GameplayConfig.AKATSUKI_EXECUTION_MAINTAIN_TICKS.get()
                    || now < execution.nextPulse) {
                continue;
            }
            execution.nextPulse = now + GameplayConfig.AKATSUKI_EXECUTION_PULSE_TICKS.get();
            float before = target.getHealth();
            boolean accepted = SoulLegacyDamageGuard.apply(() -> target.hurt(
                    executionDamage(level, attacker), execution.pulseDamage));
            float progress = Math.max(0.0F, before - target.getHealth());
            if (!target.isAlive()) {
                finish(level, execution, true);
                iterator.remove();
                continue;
            }
            if (accepted && progress > 0.0001F) {
                execution.lastProgress = now;
                if ((now - execution.startedAt) % 10L == 0L
                        && SoulResonance.isFormed(attacker.getMainHandItem(),
                                SoulResonance.Resonance.CRIMSON_THUNDER)) {
                    RaikiriChainHandler.resonanceDischarge(level, attacker, target,
                            Math.min(3.0F, execution.pulseDamage * 0.35F), 2, true,
                            0xFF334D);
                }
                if ((now - execution.startedAt) % 6L == 0L) {
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.PLAYERS, 0.34F, 0.72F + level.random.nextFloat() * 0.28F);
                }
            } else if (now - execution.lastProgress >= STALL_TICKS) {
                finish(level, execution, false);
                iterator.remove();
            }
        }
    }

    private static void renderServerAccent(ServerLevel level, LivingEntity target,
            Execution execution, long now) {
        if ((now - execution.startedAt) % 2L != 0L) return;
        double y = target.getY() + target.getBbHeight() * (0.25D + level.random.nextDouble() * 0.55D);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.92F, 0.015F, 0.04F), 0.75F),
                target.getX(), y, target.getZ(), 4,
                target.getBbWidth() * 0.7D, target.getBbHeight() * 0.18D,
                target.getBbWidth() * 0.7D, 0.01D);
        if ((now - execution.startedAt) % 6L == 0L) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), y, target.getZ(), 1,
                    target.getBbWidth() * 0.35D, 0.12D,
                    target.getBbWidth() * 0.35D, 0.0D);
        }
    }

    private static void finish(ServerLevel level, Execution execution, boolean killed) {
        Entity attackerEntity = level.getEntity(execution.attackerId);
        Entity target = level.getEntity(execution.targetId);
        if (!(attackerEntity instanceof ServerPlayer attacker)) return;
        Entity anchor = target == null ? attacker : target;
        sendVfx(level, attacker, anchor,
                BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END,
                killed ? 22 : 12, killed ? 1.0F : 0.55F, level.random.nextInt());
        if (killed) {
            level.playSound(null, anchor.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 1.05F, 0.58F);
            level.playSound(null, anchor.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.15F, 0.52F);
        } else {
            level.playSound(null, anchor.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.45F, 0.65F);
        }
    }

    private static void sendVfx(ServerLevel level, ServerPlayer attacker, Entity target,
            int type, int duration, float intensity, int seed) {
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                attacker.getX(), attacker.getY() + 0.9D, attacker.getZ(),
                target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                attacker.getYRot(), intensity, attacker.getId(), target.getId(), duration, seed);
        for (ServerPlayer viewer : level.players()) {
            if (viewer == attacker || viewer.distanceToSqr(target) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void cleanupRecords(MinecraftServer server) {
        long newestTime = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            newestTime = Math.max(newestTime, level.getGameTime());
        }
        long cutoff = newestTime - RECORD_EXPIRY_TICKS;
        RECORDS.entrySet().removeIf(entry -> entry.getValue().lastHit < cutoff
                || server.getPlayerList().getPlayer(entry.getKey().attackerId) == null);
    }

    private static ServerPlayer resolveAttacker(LivingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof IShootable shootable
                && shootable.getShooter() instanceof ServerPlayer player) {
            return player;
        }
        return direct == event.getSource().getEntity()
                && direct instanceof ServerPlayer player ? player : null;
    }

    private static boolean canAffect(ServerPlayer attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || target instanceof Player
                || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)) return false;
        if (target instanceof TamableAnimal || target instanceof AbstractVillager
                || target instanceof AbstractGolem || target instanceof WaterAnimal
                || target instanceof AmbientCreature || target instanceof Animal) return false;
        if (target instanceof Enemy) return true;
        if (target instanceof Mob mob) {
            return mob.getTarget() == attacker || mob.getLastHurtByMob() == attacker;
        }
        return target.getLastHurtByMob() == attacker;
    }

    private static float dynamicThreshold(LivingEntity target, double representative) {
        double minimum = Math.min(GameplayConfig.AKATSUKI_THRESHOLD_MIN_FRACTION.get(),
                GameplayConfig.AKATSUKI_THRESHOLD_MAX_FRACTION.get());
        double maximum = Math.max(GameplayConfig.AKATSUKI_THRESHOLD_MIN_FRACTION.get(),
                GameplayConfig.AKATSUKI_THRESHOLD_MAX_FRACTION.get());
        return AkatsukiExecutionMath.threshold(target.getMaxHealth(), representative,
                GameplayConfig.AKATSUKI_THRESHOLD_HIT_MULTIPLIER.get(), minimum, maximum);
    }

    private static boolean holdsBlade(ServerPlayer player, UUID expectedId) {
        ItemStack blade = player.getMainHandItem();
        return AkatsukiAwakening.isActive(blade)
                && blade.hasTag() && blade.getTag().hasUUID(BLADE_ID)
                && expectedId.equals(blade.getTag().getUUID(BLADE_ID));
    }

    private static UUID bladeId(ItemStack blade) {
        if (!blade.getOrCreateTag().hasUUID(BLADE_ID)) {
            blade.getOrCreateTag().putUUID(BLADE_ID, UUID.randomUUID());
        }
        return blade.getOrCreateTag().getUUID(BLADE_ID);
    }

    private static DamageSource executionDamage(ServerLevel level, ServerPlayer attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(EXECUTION_DAMAGE), attacker);
    }

    private record CombatKey(UUID attackerId, UUID targetId) {
    }

    private static final class CombatRecord {
        final UUID bladeId;
        final Deque<Float> recentDamage = new ArrayDeque<>();
        float totalDamage;
        long lastHit;
        boolean triggered;

        CombatRecord(UUID bladeId, long now) {
            this.bladeId = bladeId;
            lastHit = now;
        }

        void add(float amount, long now) {
            totalDamage += Math.max(0.0F, amount);
            recentDamage.addLast(Math.max(0.0F, amount));
            while (recentDamage.size() > 6) recentDamage.removeFirst();
            lastHit = now;
        }
    }

    private record PendingStart(ResourceKey<Level> dimension, UUID attackerId,
            UUID targetId, UUID bladeId, long startAt, float threshold, float pulseDamage) {
    }

    private static final class Execution {
        final ResourceKey<Level> dimension;
        final UUID attackerId;
        final UUID targetId;
        final UUID bladeId;
        final long startedAt;
        final float threshold;
        final float pulseDamage;
        long lastManualHit;
        long lastProgress;
        long nextPulse;

        Execution(ResourceKey<Level> dimension, UUID attackerId, UUID targetId,
                UUID bladeId, long startedAt, long lastManualHit,
                long lastProgress, long nextPulse, float threshold, float pulseDamage) {
            this.dimension = dimension;
            this.attackerId = attackerId;
            this.targetId = targetId;
            this.bladeId = bladeId;
            this.startedAt = startedAt;
            this.lastManualHit = lastManualHit;
            this.lastProgress = lastProgress;
            this.nextPulse = nextPulse;
            this.threshold = threshold;
            this.pulseDamage = pulseDamage;
        }
    }

    private AkatsukiExecution() {
    }
}
