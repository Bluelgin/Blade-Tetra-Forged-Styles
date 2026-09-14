package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.forging.LegacyFusion;
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
 * Server-owned life erosion for the NihilUL saya + Crimson Cherry hilt convergence.
 * It deliberately bypasses ordinary numerical damage reduction/caps while leaving
 * explicit invulnerability and an entity-type opt-out available to scripted bosses.
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

    private static final Map<ScarKey, Double> SCARS = new HashMap<>();
    private static final Map<HitKey, Long> NORMAL_HITS = new HashMap<>();
    private static final Map<HitKey, SaHitStamp> SA_HITS = new HashMap<>();

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
        player.getPersistentData().putInt(SA_SERIAL, serial);
        player.getPersistentData().putLong(SA_UNTIL, now + SA_MARK_WINDOW);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof EntitySlashEffect slash)
                || !(slash.getShooter() instanceof ServerPlayer player)
                || !isDeadThought(player.getMainHandItem())
                || player.getPersistentData().getLong(SA_UNTIL) < level.getGameTime()) {
            return;
        }
        int serial = player.getPersistentData().getInt(SA_SERIAL);
        if (serial > 0) {
            event.getEntity().getPersistentData().putInt(SA_ENTITY_SERIAL, serial);
        }
    }

    /**
     * LOWEST + receiveCanceled is intentional: Life Erosion is not conventional
     * damage, so numeric shields/caps may cancel the attack while the attempted
     * Dead Thought cut still erodes maximum life.
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
                erode(level, target, FINAL_SCENE_EROSION);
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
        erode(level, target, NORMAL_EROSION);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || target.isInvulnerable()) {
            return;
        }
        Double erosion = SCARS.get(new ScarKey(level.dimension(), target.getUUID()));
        if (erosion == null) return;
        float cap = DeadThoughtErosionMath.effectiveCap(
                target.getMaxHealth(), erosion, ENGINE_HEALTH_FLOOR);
        float room = Math.max(0.0F, cap - target.getHealth());
        if (event.getAmount() > room) {
            event.setAmount(room);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        removeEntity(event.getEntity());
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
        Iterator<Map.Entry<ScarKey, Double>> iterator = SCARS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ScarKey, Double> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            Entity found = level == null ? null : level.getEntity(entry.getKey().entityId());
            if (!(found instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }
            if (target.isInvulnerable()) continue;
            float cap = DeadThoughtErosionMath.effectiveCap(
                    target.getMaxHealth(), entry.getValue(), ENGINE_HEALTH_FLOOR);
            if (target.getHealth() > cap) {
                target.setHealth(cap);
            }
        }

        long oldest = server.getTickCount() - 200L;
        NORMAL_HITS.entrySet().removeIf(entry -> entry.getValue() < oldest);
        SA_HITS.entrySet().removeIf(entry -> entry.getValue().tick() < oldest);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceKey<Level> dimension = level.dimension();
        SCARS.keySet().removeIf(key -> key.dimension().equals(dimension));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SCARS.clear();
        NORMAL_HITS.clear();
        SA_HITS.clear();
    }

    private static void erode(ServerLevel level, LivingEntity target, double amount) {
        ScarKey key = new ScarKey(level.dimension(), target.getUUID());
        double erosion = DeadThoughtErosionMath.add(SCARS.getOrDefault(key, 0.0D), amount);
        SCARS.put(key, erosion);
        float cap = DeadThoughtErosionMath.effectiveCap(
                target.getMaxHealth(), erosion, ENGINE_HEALTH_FLOOR);
        if (target.getHealth() > cap && !target.isInvulnerable()) {
            target.setHealth(cap);
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

    private static void removeEntity(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        UUID id = entity.getUUID();
        SCARS.remove(new ScarKey(level.dimension(), id));
        NORMAL_HITS.keySet().removeIf(key -> key.target().equals(id));
        SA_HITS.keySet().removeIf(key -> key.target().equals(id));
    }

    private record ScarKey(ResourceKey<Level> dimension, UUID entityId) {}
    private record HitKey(UUID attacker, UUID target) {}
    private record SaHitStamp(int serial, long tick) {}

    private DeadThoughtFusionHandler() {
    }
}
