package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.RaikiriVfxPacket;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic chain conduction unique to an awakened Raikiri. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaikiriChainHandler {
    private static final String NORMAL_COOLDOWN = "blade_tetra_raikiri_chain_cooldown";
    private static final String OVERLOAD_COOLDOWN = "blade_tetra_raikiri_overload_cooldown";
    private static final String SA_SERIAL = "blade_tetra_raikiri_sa_serial";
    private static final String SA_CONFIRMED_UNTIL = "blade_tetra_raikiri_sa_confirmed_until";
    private static final String SA_DIRECT_UNTIL = "blade_tetra_raikiri_sa_direct_until";
    private static final String SA_PROJECTILE_SERIAL = "blade_tetra_raikiri_sa_projectile_serial";

    private static final int NORMAL_COOLDOWN_TICKS = 24;
    private static final int OVERLOAD_COOLDOWN_TICKS = 80;
    private static final int SA_PROJECTILE_WINDOW_TICKS = 30;
    private static final double NORMAL_DAMAGE_RATIO = 0.20D;
    private static final double OVERLOAD_DAMAGE_RATIO = 0.35D;
    private static final double NORMAL_DAMAGE_CAP = 6.0D;
    private static final double OVERLOAD_DAMAGE_CAP = 10.0D;
    private static final double DAMAGE_DECAY = 0.85D;
    private static final double DRY_RADIUS = 6.0D;
    private static final double WET_RADIUS = 8.0D;

    private static final Map<UUID, PendingStrike> PENDING_STRIKES = new HashMap<>();
    private static final Set<UUID> APPLYING_CHAIN_DAMAGE = new HashSet<>();

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (event.getType() != SlashArts.ArtsType.Success
                || !(event.getEntityLiving() instanceof ServerPlayer player)
                || !isActive(player.getMainHandItem())) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int serial = player.getPersistentData().getInt(SA_SERIAL) + 1;
        player.getPersistentData().putInt(SA_SERIAL, serial);
        player.getPersistentData().putLong(SA_CONFIRMED_UNTIL,
                now + SA_PROJECTILE_WINDOW_TICKS);
        player.getPersistentData().putLong(SA_DIRECT_UNTIL, now + 2L);

        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(64.0D),
                candidate -> candidate.tickCount <= 2
                        && candidate instanceof IShootable shootable
                        && shootable.getShooter() == player)) {
            entity.getPersistentData().putInt(SA_PROJECTILE_SERIAL, serial);
        }

        PendingStrike pending = PENDING_STRIKES.get(player.getUUID());
        if (pending != null && pending.hitTick == now) {
            PENDING_STRIKES.remove(player.getUUID());
            discharge(level, player, player.getMainHandItem(), pending.origin,
                    pending.primaryTargetId, pending.damage, true, now);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof IShootable shootable)
                || !(shootable.getShooter() instanceof ServerPlayer player)
                || !isActive(player.getMainHandItem())) return;
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now) {
            event.getEntity().getPersistentData().putInt(SA_PROJECTILE_SERIAL,
                    player.getPersistentData().getInt(SA_SERIAL));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F
                || BladeTechniqueHandler.isTechniqueDamage(event.getSource())
                || SoulLegacyDamageGuard.isSecondary(event.getSource())) return;
        ServerPlayer player = resolveAttacker(event);
        if (player == null || APPLYING_CHAIN_DAMAGE.contains(player.getUUID())) return;
        ItemStack blade = player.getMainHandItem();
        if (!isActive(blade) || !canAffect(player, event.getEntity())) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Entity direct = event.getSource().getDirectEntity();
        int serial = player.getPersistentData().getInt(SA_SERIAL);
        boolean markedProjectile = direct != null && serial != 0
                && direct.getPersistentData().getInt(SA_PROJECTILE_SERIAL) == serial;
        boolean confirmedSa = player.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now
                && (markedProjectile || direct == player
                        && player.getPersistentData().getLong(SA_DIRECT_UNTIL) >= now);
        Vec3 origin = event.getEntity().getBoundingBox().getCenter();
        if (confirmedSa) {
            PENDING_STRIKES.remove(player.getUUID());
            discharge(level, player, blade, origin, event.getEntity().getUUID(),
                    event.getAmount(), true, now);
            return;
        }

        // SlashBlade may post the SA event immediately after its damage event.
        // Delaying an ordinary chain by one tick lets that hit be upgraded instead
        // of producing both an ordinary chain and an overload.
        PENDING_STRIKES.put(player.getUUID(), new PendingStrike(
                level.dimension().location(), event.getEntity().getUUID(), origin,
                event.getAmount(), now, now + 1L));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_STRIKES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, PendingStrike> entry : PENDING_STRIKES.entrySet()) {
            PendingStrike pending = entry.getValue();
            ServerLevel level = findLevel(server, pending.dimension);
            if (level == null || level.getGameTime() < pending.releaseTick) continue;
            completed.add(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level) continue;
            ItemStack blade = player.getMainHandItem();
            if (!isActive(blade)) continue;
            discharge(level, player, blade, pending.origin, pending.primaryTargetId,
                    pending.damage, false, level.getGameTime());
        }
        completed.forEach(PENDING_STRIKES::remove);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PENDING_STRIKES.entrySet().removeIf(entry ->
                entry.getValue().dimension.equals(level.dimension().location()));
    }

    private static void discharge(ServerLevel level, ServerPlayer player, ItemStack blade,
            Vec3 origin, UUID primaryTargetId, float triggeringDamage,
            boolean overload, long now) {
        if (!GameplayConfig.ENABLE_OFFENSIVE_LIGHTNING.get()) return;
        String cooldownKey = overload ? OVERLOAD_COOLDOWN : NORMAL_COOLDOWN;
        if (blade.getOrCreateTag().getLong(cooldownKey) > now) return;

        Entity primaryEntity = level.getEntity(primaryTargetId);
        boolean wet = primaryEntity instanceof LivingEntity living && isWet(level, living);
        int conductivity = ConductiveBladeHandler.conductivityScore(blade);
        int hops = 2 + conductivity / 2 + (overload ? 2 : 0) + (wet ? 1 : 0);
        double radius = wet ? WET_RADIUS : DRY_RADIUS;
        double ratio = overload ? OVERLOAD_DAMAGE_RATIO : NORMAL_DAMAGE_RATIO;
        double cap = overload ? OVERLOAD_DAMAGE_CAP : NORMAL_DAMAGE_CAP;

        Set<UUID> visited = new HashSet<>();
        visited.add(primaryTargetId);
        Vec3 from = origin;
        int completedHops = 0;
        APPLYING_CHAIN_DAMAGE.add(player.getUUID());
        try {
            for (int hop = 0; hop < hops; hop++) {
                LivingEntity target = nearestTarget(level, player, from, radius, visited);
                if (target == null) break;
                visited.add(target.getUUID());
                Vec3 to = target.getBoundingBox().getCenter();
                emitRaikiriArc(level, player, from, to, hop, overload);
                float damage = (float) (Math.min(triggeringDamage * ratio, cap)
                        * Math.pow(DAMAGE_DECAY, hop));
                SoulLegacyDamageGuard.apply(() -> target.hurt(
                        level.damageSources().playerAttack(player), damage));
                from = to;
                completedHops++;
            }
        } finally {
            APPLYING_CHAIN_DAMAGE.remove(player.getUUID());
        }
        if (completedHops <= 0) return;

        blade.getOrCreateTag().putLong(cooldownKey,
                now + (overload ? OVERLOAD_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS));
        if (overload) {
            // Prevent a multi-hit SA from producing several overload networks.
            player.getPersistentData().remove(SA_CONFIRMED_UNTIL);
            player.getPersistentData().remove(SA_DIRECT_UNTIL);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, overload ? 0.75F : 0.42F,
                overload ? 1.18F : 1.48F);
    }

    /**
     * Spends a small, fixed resonance budget on enemies around an existing soul
     * technique. This deliberately bypasses the normal Raikiri proc path so a
     * reflected cut, petal sword, or execution pulse cannot start a proc loop.
     */
    public static void resonanceDischarge(ServerLevel level, ServerPlayer player,
            LivingEntity originTarget, float totalBudget, int maxHops,
            boolean overload) {
        resonanceDischarge(level, player, originTarget, totalBudget, maxHops,
                overload, 0xFFFFFF);
    }

    public static void resonanceDischarge(ServerLevel level, ServerPlayer player,
            LivingEntity originTarget, float totalBudget, int maxHops,
            boolean overload, int color) {
        ItemStack blade = player.getMainHandItem();
        if (!isActive(blade) || totalBudget <= 0.0F || maxHops <= 0) return;
        Set<UUID> visited = new HashSet<>();
        visited.add(originTarget.getUUID());
        Vec3 from = originTarget.getBoundingBox().getCenter();
        double radius = isWet(level, originTarget) ? WET_RADIUS : DRY_RADIUS;
        int completed = 0;
        APPLYING_CHAIN_DAMAGE.add(player.getUUID());
        try {
            for (int hop = 0; hop < maxHops; hop++) {
                LivingEntity target = nearestTarget(level, player, from, radius, visited);
                if (target == null) break;
                visited.add(target.getUUID());
                Vec3 to = target.getBoundingBox().getCenter();
                emitRaikiriArc(level, player, from, to, hop, overload, color);
                float damage = (float) (totalBudget / maxHops
                        * Math.pow(DAMAGE_DECAY, hop));
                SoulLegacyDamageGuard.apply(() -> target.hurt(
                        level.damageSources().playerAttack(player), damage));
                from = to;
                completed++;
            }
        } finally {
            APPLYING_CHAIN_DAMAGE.remove(player.getUUID());
        }
        if (completed > 0) {
            level.playSound(null, originTarget.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                    overload ? 0.62F : 0.38F, overload ? 1.05F : 1.36F);
        }
    }

    private static LivingEntity nearestTarget(ServerLevel level, ServerPlayer player,
            Vec3 center, double radius, Set<UUID> visited) {
        return level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(center, center).inflate(radius),
                        target -> !visited.contains(target.getUUID())
                                && canAffect(player, target))
                .stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(center)))
                .orElse(null);
    }

    private static void emitRaikiriArc(ServerLevel level, ServerPlayer player,
            Vec3 from, Vec3 to, int hop, boolean overload) {
        emitRaikiriArc(level, player, from, to, hop, overload, 0xFFFFFF);
    }

    private static void emitRaikiriArc(ServerLevel level, ServerPlayer player,
            Vec3 from, Vec3 to, int hop, boolean overload, int color) {
        int seed = player.getRandom().nextInt() ^ hop * 0x45D9F3B;
        RaikiriVfxPacket packet = new RaikiriVfxPacket(
                from.x, from.y, from.z, to.x, to.y, to.z, overload, color, seed);
        Vec3 center = from.add(to).scale(0.5D);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD,
                to.x, to.y, to.z, overload ? 5 : 2,
                0.16D, 0.22D, 0.16D, 0.015D);
    }

    private static ServerPlayer resolveAttacker(LivingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof IShootable shootable
                && shootable.getShooter() instanceof ServerPlayer player) return player;
        return event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
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

    private static boolean isWet(ServerLevel level, LivingEntity entity) {
        return entity.isInWaterRainOrBubble()
                || level.isRainingAt(entity.blockPosition());
    }

    private static boolean isActive(ItemStack blade) {
        return SoulLegacyState.isActive(blade, SoulLegacyState.Legacy.RAIKIRI);
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private record PendingStrike(ResourceLocation dimension, UUID primaryTargetId,
            Vec3 origin, float damage, long hitTick, long releaseTick) {
    }

    private RaikiriChainHandler() {
    }
}
