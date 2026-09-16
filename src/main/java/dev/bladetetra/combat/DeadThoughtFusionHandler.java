package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned soul erosion for the NihilUL saya + Crimson Cherry hilt convergence.
 *
 * <p>The stored erosion value is intentionally unbounded. Before the soul reaches zero,
 * it behaves as the existing virtual maximum-life ceiling. Crossing 100% erosion requests
 * a normal lethal player-damage handshake. If the target survives because a boss cap,
 * phase transition, totem/death hook, or other rule refuses that death, the target enters
 * SOUL_BROKEN instead of being spam-killed every tick. While broken, the virtual health
 * clamp is suspended so the foreign boss state machine can keep running. A later complete
 * Blood Cherry Final Scene may then request the terminal soul-collapse death directly.
 * Explicit invulnerability and the entity-type opt-out remain hard compatibility exits.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeadThoughtFusionHandler {
    static final double NORMAL_EROSION = 0.0025D;
    static final double FINAL_SCENE_EROSION = 0.05D;
    static final int NORMAL_HIT_COOLDOWN = 4;
    static final int SA_MARK_WINDOW = 40;
    static final float ENGINE_HEALTH_FLOOR = 0.01F;

    private static final String SA_SERIAL = "blade_tetra_dead_thought_sa_serial";
    private static final String SA_UNTIL = "blade_tetra_dead_thought_sa_until";
    private static final String SA_ENTITY_SERIAL = "blade_tetra_dead_thought_entity_serial";
    private static final TagKey<EntityType<?>> IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "dead_thought_immune"));

    private static final Map<ScarKey, SoulRecord> SOULS = new HashMap<>();
    private static final Map<HitKey, Long> NORMAL_HITS = new HashMap<>();
    private static final Map<HitKey, SaHitStamp> SA_HITS = new HashMap<>();
    private static final Map<UUID, PendingSlash> PENDING_SA_SLASHES = new HashMap<>();

    /**
     * LivingDeathEvent is posted synchronously from LivingEntity#die. Vanilla isAlive()
     * still depends on health, so a direct die() call against a positive-health entity
     * cannot be judged reliably with isAlive() alone. This probe observes whether Forge's
     * death event was allowed to complete without pre-setting health to zero.
     */
    private static final ThreadLocal<DeathProbe> DEATH_PROBE = new ThreadLocal<>();

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayer player)
                || event.getType() == SlashArts.ArtsType.Fail
                || !isDeadThought(player.getMainHandItem())) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        int serial = player.getPersistentData().getInt(SA_SERIAL) + 1;
        if (serial <= 0) serial = 1;
        PENDING_SA_SLASHES.remove(player.getUUID());
        player.getPersistentData().putInt(SA_SERIAL, serial);
        player.getPersistentData().putLong(SA_UNTIL, now + SA_MARK_WINDOW);
        if (ModSlashBladeAbilities.BLOOD_CHERRY_FINAL_SCENE.getId()
                .equals(event.getSlashBladeState().getSlashArtsKey())) {
            DeadThoughtVisuals.start(player,
                    event.getSlashBladeState().getTargetEntity(player.level()), serial);
        }
    }

    /**
     * Sakura End posts this event immediately before adding its slash entity.
     * Issue a one-shot marker here so unrelated slash effects created during the
     * broader SA animation window cannot inherit Final Scene erosion.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDoSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || !isDeadThought(event.getBlade())
                || !ModSlashBladeAbilities.BLOOD_CHERRY_FINAL_SCENE.getId()
                        .equals(event.getSlashBladeState().getSlashArtsKey())
                || !isFinalSceneCombo(event.getSlashBladeState().getComboSeq())
                || player.getPersistentData().getLong(SA_UNTIL)
                        < player.serverLevel().getGameTime()) {
            return;
        }
        int serial = player.getPersistentData().getInt(SA_SERIAL);
        if (serial > 0) {
            PENDING_SA_SLASHES.put(player.getUUID(),
                    new PendingSlash(serial, player.server.getTickCount()));
            DeadThoughtVisuals.slash(player, serial, event.getSlashBladeState().getComboSeq());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof ServerPlayer player)) {
            return;
        }
        PendingSlash pending = PENDING_SA_SLASHES.remove(player.getUUID());
        if (pending != null && pending.serverTick() == player.server.getTickCount()) {
            event.getEntity().getPersistentData().putInt(
                    SA_ENTITY_SERIAL, pending.serial());
        }
    }

    /**
     * LOWEST + receiveCanceled is intentional: soul erosion is not conventional
     * damage, so numeric shields/caps may cancel the attempted cut while Dead Thought
     * still attacks the target's life-bearing soul.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || SoulLegacyDamageGuard.isSecondary(event.getSource())) {
            return;
        }

        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        int saSerial = direct == null ? 0
                : direct.getPersistentData().getInt(SA_ENTITY_SERIAL);
        if (saSerial > 0 && direct instanceof IShootable shootable
                && shootable.getShooter() instanceof ServerPlayer player
                && isDeadThought(player.getMainHandItem())
                && canErode(player, target)) {
            HitKey hit = new HitKey(player.getUUID(), target.getUUID());
            SaHitStamp previous = SA_HITS.get(hit);
            if (previous == null || previous.serial() != saSerial) {
                SA_HITS.put(hit, new SaHitStamp(saSerial, level.getGameTime()));
                SoulUpdate update = erode(level, player, target,
                        FINAL_SCENE_EROSION, true);
                publishVisualUpdate(player, target, saSerial, update);
            }
            return;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)
                || direct != player
                || !isDeadThought(player.getMainHandItem())
                || !canErode(player, target)) {
            return;
        }
        HitKey hit = new HitKey(player.getUUID(), target.getUUID());
        long now = level.getGameTime();
        Long previous = NORMAL_HITS.get(hit);
        if (previous != null && now - previous < NORMAL_HIT_COOLDOWN) {
            return;
        }
        NORMAL_HITS.put(hit, now);
        SoulUpdate update = erode(level, player, target, NORMAL_EROSION, false);
        publishVisualUpdate(player, target, 0, update);
    }

    @SubscribeEvent
    public static void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof LivingEntity target) {
            SoulRecord soul = SOULS.get(new ScarKey(observer.level().dimension(), target.getUUID()));
            if (soul != null && (soul.broken || soul.erosion >= .70D)) {
                DeadThoughtVisuals.tracking(observer, target, soul.erosion, soul.broken);
            }
        }
    }

    /**
     * Observe the current synchronous Dead Thought death request at the last Forge priority.
     * If another mod cancels the death event, the request is treated as refused and the
     * entity remains SOUL_BROKEN. We do not mutate the event here.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeathProbe(LivingDeathEvent event) {
        DeathProbe probe = DEATH_PROBE.get();
        if (probe == null || !probe.target.equals(event.getEntity().getUUID())) return;
        probe.seen = true;
        probe.canceled = event.isCanceled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || target.isInvulnerable()) {
            return;
        }
        SoulRecord soul = SOULS.get(new ScarKey(level.dimension(), target.getUUID()));
        if (soul == null || soul.broken) return;
        float cap = DeadThoughtErosionMath.effectiveCap(
                target.getMaxHealth(), soul.erosion, ENGINE_HEALTH_FLOOR);
        float room = Math.max(0.0F, cap - target.getHealth());
        if (event.getAmount() > room) {
            event.setAmount(room);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            removeEntity(living);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<ScarKey, SoulRecord>> iterator = SOULS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ScarKey, SoulRecord> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            Entity found = level == null ? null : level.getEntity(entry.getKey().entityId());
            if (!(found instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }
            SoulRecord soul = entry.getValue();
            // Once another mod/world rule has refused soul collapse, do not force the
            // old 0.01 HP cap every tick. The foreign phase is allowed to run normally;
            // only a later complete Final Scene can request terminal collapse.
            if (soul.broken || target.isInvulnerable()) continue;
            float cap = DeadThoughtErosionMath.effectiveCap(
                    target.getMaxHealth(), soul.erosion, ENGINE_HEALTH_FLOOR);
            if (target.getHealth() > cap) {
                target.setHealth(cap);
            }
        }

        long oldest = server.getTickCount() - 200L;
        NORMAL_HITS.entrySet().removeIf(entry -> entry.getValue() < oldest);
        SA_HITS.entrySet().removeIf(entry -> entry.getValue().tick() < oldest);
        PENDING_SA_SLASHES.entrySet().removeIf(
                entry -> entry.getValue().serverTick() < server.getTickCount());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceKey<Level> dimension = level.dimension();
        SOULS.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SOULS.clear();
        NORMAL_HITS.clear();
        SA_HITS.clear();
        PENDING_SA_SLASHES.clear();
        DEATH_PROBE.remove();
    }

    private static SoulUpdate erode(ServerLevel level, ServerPlayer attacker,
            LivingEntity target, double amount, boolean finalScene) {
        ScarKey key = new ScarKey(level.dimension(), target.getUUID());
        SoulRecord soul = SOULS.computeIfAbsent(key, ignored -> new SoulRecord());
        soul.erosion = DeadThoughtErosionMath.add(soul.erosion, amount);

        if (soul.broken) {
            if (!finalScene) {
                return new SoulUpdate(soul.erosion, SoulOutcome.BROKEN_ALREADY);
            }
            boolean collapsed = attemptTerminalCollapse(level, attacker, target);
            return new SoulUpdate(soul.erosion,
                    collapsed ? SoulOutcome.COLLAPSED : SoulOutcome.COLLAPSE_REJECTED);
        }

        if (DeadThoughtErosionMath.isSoulBroken(soul.erosion)) {
            if (attemptCollapseHandshake(level, attacker, target)) {
                return new SoulUpdate(soul.erosion, SoulOutcome.COLLAPSED);
            }
            soul.broken = true;
            return new SoulUpdate(soul.erosion, SoulOutcome.BROKEN_NOW);
        }

        float cap = DeadThoughtErosionMath.effectiveCap(
                target.getMaxHealth(), soul.erosion, ENGINE_HEALTH_FLOOR);
        if (target.getHealth() > cap && !target.isInvulnerable()) {
            target.setHealth(cap);
        }
        return new SoulUpdate(soul.erosion, SoulOutcome.ERODED);
    }

    /**
     * First soul-zero transition: use a real player damage request so the target's
     * normal hurt/death hooks, phase changes, totems and third-party damage caps get
     * one opportunity to answer. Surviving this request means SOUL_BROKEN.
     */
    private static boolean attemptCollapseHandshake(ServerLevel level,
            ServerPlayer attacker, LivingEntity target) {
        float amount = collapseHandshakeDamage(target);
        DamageSource source = level.damageSources().playerAttack(attacker);
        return observeDeath(target, () -> SoulLegacyDamageGuard.apply(
                () -> target.hurt(source, amount)));
    }

    /**
     * A later complete Final Scene against SOUL_BROKEN asks the entity's normal death
     * pipeline directly. We observe LivingDeathEvent instead of relying on isAlive(),
     * because vanilla isAlive() is health-based and die() can be called with positive HP.
     * Only after Forge accepts the death do we set health to zero for vanilla death ticks.
     */
    private static boolean attemptTerminalCollapse(ServerLevel level,
            ServerPlayer attacker, LivingEntity target) {
        DamageSource source = level.damageSources().playerAttack(attacker);
        boolean accepted = observeDeath(target, () -> SoulLegacyDamageGuard.apply(() -> {
            target.die(source);
            return true;
        }));
        if (accepted && target.isAlive()) {
            target.setHealth(0.0F);
        }
        return accepted;
    }

    /**
     * Returns true when the synchronous death event was observed and not canceled.
     * If a custom entity dies/removes itself without posting LivingDeathEvent, fall back
     * to its final alive state. A nested probe is restored defensively after the call.
     */
    private static boolean observeDeath(LivingEntity target, Runnable request) {
        DeathProbe previous = DEATH_PROBE.get();
        DeathProbe probe = new DeathProbe(target.getUUID());
        DEATH_PROBE.set(probe);
        try {
            request.run();
        } finally {
            if (previous == null) DEATH_PROBE.remove();
            else DEATH_PROBE.set(previous);
        }
        if (probe.seen) return !probe.canceled;
        return !target.isAlive();
    }

    static float collapseHandshakeDamage(LivingEntity target) {
        double health = Math.max(0.0D, target.getHealth());
        double max = Math.max(1.0D, target.getMaxHealth());
        double scaled = Math.max(1024.0D, max * 64.0D + health + 1.0D);
        return (float) Math.min(Float.MAX_VALUE / 1024.0D, scaled);
    }

    private static void publishVisualUpdate(ServerPlayer attacker, LivingEntity target,
            int serial, SoulUpdate update) {
        if (update.outcome == SoulOutcome.COLLAPSED) {
            DeadThoughtVisuals.soulCollapse(attacker, target, serial);
            return;
        }
        DeadThoughtVisuals.erosion(attacker, target, update.erosion, serial);
        if (update.outcome == SoulOutcome.BROKEN_NOW
                || update.outcome == SoulOutcome.COLLAPSE_REJECTED) {
            DeadThoughtVisuals.soulBroken(attacker, target, update.erosion, serial);
        }
    }

    private static boolean canErode(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || target.isInvulnerable() || target.getType().is(IMMUNE)
                || !LegacyFusionCombatSupport.canAffect(player, target)) {
            return false;
        }
        return !(target instanceof Player other)
                || !other.isCreative() && !other.isSpectator();
    }

    private static boolean isDeadThought(ItemStack stack) {
        return LegacyFusion.active(stack)
                == LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT;
    }

    static boolean isFinalSceneCombo(ResourceLocation combo) {
        return combo != null && DeadThoughtSlashProvenance.isFinalSceneCombo(
                combo.getNamespace(), combo.getPath());
    }

    private static void removeEntity(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        UUID id = entity.getUUID();
        SOULS.remove(new ScarKey(level.dimension(), id));
        NORMAL_HITS.keySet().removeIf(key -> key.target().equals(id));
        SA_HITS.keySet().removeIf(key -> key.target().equals(id));
    }

    private static final class SoulRecord {
        double erosion;
        boolean broken;
    }

    private static final class DeathProbe {
        final UUID target;
        boolean seen;
        boolean canceled;

        DeathProbe(UUID target) {
            this.target = target;
        }
    }

    private enum SoulOutcome {
        ERODED,
        BROKEN_NOW,
        BROKEN_ALREADY,
        COLLAPSE_REJECTED,
        COLLAPSED
    }

    private record SoulUpdate(double erosion, SoulOutcome outcome) {}
    private record ScarKey(ResourceKey<Level> dimension, UUID entityId) {}
    private record HitKey(UUID attacker, UUID target) {}
    private record SaHitStamp(int serial, long tick) {}
    private record PendingSlash(int serial, long serverTick) {}

    private DeadThoughtFusionHandler() {
    }
}
