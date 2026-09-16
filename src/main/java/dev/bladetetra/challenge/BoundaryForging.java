package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.Tags;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Mikage's graduation forging: every successful Slash Art can call down one gate. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoundaryForging {
    private static final String PLAYER_COOLDOWN = "blade_tetra_boundary_cooldown_until";
    private static final String SA_CONFIRMED_UNTIL = "blade_tetra_boundary_sa_confirmed_until";
    private static final String SA_DIRECT_UNTIL = "blade_tetra_boundary_sa_direct_until";
    private static final String SA_SERIAL = "blade_tetra_boundary_sa_serial";
    private static final String SA_RECENT_TARGET = "blade_tetra_boundary_sa_recent_target";
    private static final String SA_RECENT_TARGET_AT = "blade_tetra_boundary_sa_recent_target_at";
    private static final String SA_PROJECTILE_SERIAL = "blade_tetra_boundary_sa_projectile_serial";
    private static final String VISUAL_SWORD = "blade_tetra_boundary_visual_sword";

    // Legacy per-blade state is discarded the next time the forged blade performs a Slash Art.
    private static final String[] LEGACY_TAGS = {
            "blade_tetra_boundary_mark",
            "blade_tetra_boundary_mark_since",
            "blade_tetra_boundary_progress",
            "blade_tetra_boundary_last_hit",
            "blade_tetra_boundary_cooldown",
            "blade_tetra_boundary_iaido_precise"
    };

    private static final int COOLDOWN_TICKS = 200;
    private static final int SA_PROJECTILE_WINDOW_TICKS = 60;
    private static final int SWORD_INTERVAL_TICKS = 3;
    private static final int LOCK_TICK = 15;
    private static final int IMPACT_TICK = 31;
    private static final int SUPPRESSION_TICKS = 32;
    private static final int FLAME_DURATION_TICKS = 80;
    private static final int FLAME_PULSES = 8;
    private static final int FLAME_INTERVAL_TICKS = 10;
    private static final float FLAME_TOTAL_ATTACK_DAMAGE = 0.60F;
    private static final float FLAME_MAX_HEALTH_DAMAGE_PER_PULSE = 0.004F;
    private static final int SWORD_COLOR = 0xB71936;
    private static final float MAX_HEALTH_DAMAGE = 0.10F;
    private static final double IMPACT_RADIUS = 5.0D;
    private static final List<BoundaryStrike> STRIKES = new ArrayList<>();

    private static final ResourceKey<DamageType> BOUNDARY_FLASH = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "boundary_flash"));
    private static final ResourceKey<DamageType> BOUNDARY_SEAL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "boundary_seal"));
    private static final ResourceKey<DamageType> BOUNDARY_FLAME = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "boundary_flame"));

    public static boolean isForged(ItemStack stack) {
        return ForgingImprovements.has(stack, ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.BOUNDARY_FORGING);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (isForged(stack)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.boundary_forged")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Repairs scrolls awarded by development builds that stored Tetra's full key. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void migrateBoundaryScroll(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ScrollItem.instance)) {
            return;
        }
        ScrollData data = ScrollData.read(stack);
        if ("blade_tetra.boundary_forging".equals(data.key)
                && !"blade_tetra.boundary_forging".equals(data.details)) {
            data.details = "blade_tetra.boundary_forging";
            data.write(stack);
        }
    }

    @SubscribeEvent
    public static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        LivingEntity user = event.getEntityLiving();
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack blade = user.getMainHandItem();
        if (!isForged(blade) || event.getType() != SlashArts.ArtsType.Success) {
            clearPendingArt(user);
            return;
        }
        clearLegacyState(blade);
        long now = level.getGameTime();
        if (user.getPersistentData().getLong(PLAYER_COOLDOWN) > now) {
            clearPendingArt(user);
            return;
        }

        int serial = user.getPersistentData().getInt(SA_SERIAL) + 1;
        user.getPersistentData().putInt(SA_SERIAL, serial);
        user.getPersistentData().putLong(SA_CONFIRMED_UNTIL,
                now + SA_PROJECTILE_WINDOW_TICKS);
        user.getPersistentData().putLong(SA_DIRECT_UNTIL, now + 2L);

        // Projectiles may be added during doArts(), immediately before this event.
        // Mark every fresh SlashBlade shootable owned by this player.
        for (Entity entity : level.getEntities(user, user.getBoundingBox().inflate(64.0D),
                candidate -> candidate.tickCount <= 2
                        && candidate instanceof IShootable shootable
                        && shootable.getShooter() == user
                        && !candidate.getPersistentData().getBoolean(VISUAL_SWORD))) {
            entity.getPersistentData().putInt(SA_PROJECTILE_SERIAL, serial);
        }

        if (user.getPersistentData().hasUUID(SA_RECENT_TARGET)
                && user.getPersistentData().getLong(SA_RECENT_TARGET_AT) == now) {
            Entity recent = level.getEntity(user.getPersistentData().getUUID(SA_RECENT_TARGET));
            if (recent instanceof LivingEntity target && canAffect(user, target)) {
                consumeArt(level, user, target, now);
            }
        }
    }

    /** Tags delayed SA entities even when they are spawned after the perform event. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof IShootable shootable)
                || !(shootable.getShooter() instanceof LivingEntity user)
                || event.getEntity().getPersistentData().getBoolean(VISUAL_SWORD)) {
            return;
        }
        long now = level.getGameTime();
        if (user.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now
                && isForged(user.getMainHandItem())) {
            event.getEntity().getPersistentData().putInt(SA_PROJECTILE_SERIAL,
                    user.getPersistentData().getInt(SA_SERIAL));
        }
    }

    /** Uses real, post-mitigation damage instead of SlashBlade's melee-only HitEvent. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getAmount() <= 0.0F) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity causing = event.getSource().getEntity();
        LivingEntity user = causing instanceof LivingEntity living ? living : null;
        if (direct instanceof IShootable shootable
                && shootable.getShooter() instanceof LivingEntity shooter) {
            user = shooter;
        }
        if (user == null || !canAffect(user, target)) {
            return;
        }

        long now = level.getGameTime();
        int serial = user.getPersistentData().getInt(SA_SERIAL);
        boolean markedProjectile = direct != null
                && direct.getPersistentData().getInt(SA_PROJECTILE_SERIAL) == serial
                && serial != 0;
        boolean forgedBladeDamage = isForged(user.getMainHandItem())
                && (direct == user || direct instanceof IShootable);
        if (!markedProjectile && !forgedBladeDamage) {
            return;
        }

        boolean confirmedSource = markedProjectile
                || (direct == user
                        && user.getPersistentData().getLong(SA_DIRECT_UNTIL) >= now);
        if (user.getPersistentData().getLong(SA_CONFIRMED_UNTIL) >= now
                && confirmedSource) {
            if (user.getPersistentData().getLong(PLAYER_COOLDOWN) <= now) {
                consumeArt(level, user, target, now);
            }
        } else if (forgedBladeDamage) {
            // Some SA implementations deal their immediate damage before posting
            // PerformSlashArtEvent. A same-tick record cannot leak into a later
            // ordinary attack, and also covers zero/very-short charge arts.
            user.getPersistentData().putUUID(SA_RECENT_TARGET, target.getUUID());
            user.getPersistentData().putLong(SA_RECENT_TARGET_AT, now);
        }
    }

    /** Visual summoned swords are never allowed to hurt or stagger anything they cross. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelVisualSwordDamage(LivingAttackEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct != null && direct.getPersistentData().getBoolean(VISUAL_SWORD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STRIKES.isEmpty()) {
            return;
        }
        Iterator<BoundaryStrike> iterator = STRIKES.iterator();
        while (iterator.hasNext()) {
            BoundaryStrike strike = iterator.next();
            ServerLevel level = event.getServer().getLevel(strike.dimension);
            if (level == null || !strike.tick(level, level.getGameTime())) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        STRIKES.clear();
    }

    private static void beginStrike(ServerLevel level, LivingEntity user,
            LivingEntity target, long now) {
        double attackDamage = Math.max(1.0D,
                user.getAttributeValue(Attributes.ATTACK_DAMAGE));
        double orientation = Math.toRadians(user.getYRot()) + Math.PI * 0.25D;
        STRIKES.add(new BoundaryStrike(level.dimension(), user.getUUID(), target.getUUID(),
                now, attackDamage, orientation, target.getBoundingBox().getCenter()));
        Vec3 center = target.getBoundingBox().getCenter();
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_STRIKE,
                center.x, center.y, center.z, center.x, center.y, center.z,
                (float) Math.toDegrees(orientation), 1.0F,
                user.getId(), target.getId(), 82, level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.7F, 1.45F);
    }

    private static void consumeArt(ServerLevel level, LivingEntity user,
            LivingEntity target, long now) {
        clearPendingArt(user);
        user.getPersistentData().putLong(PLAYER_COOLDOWN, now + COOLDOWN_TICKS);
        beginStrike(level, user, target, now);
    }

    private static void clearPendingArt(LivingEntity user) {
        user.getPersistentData().remove(SA_CONFIRMED_UNTIL);
        user.getPersistentData().remove(SA_DIRECT_UNTIL);
        user.getPersistentData().remove(SA_RECENT_TARGET);
        user.getPersistentData().remove(SA_RECENT_TARGET_AT);
    }

    private static void launchVisualSword(ServerLevel level, LivingEntity user,
            LivingEntity target, int index, double orientation) {
        Vec3 center = target.getBoundingBox().getCenter();
        double angle = orientation + index * Math.PI * 0.5D;
        double radius = 3.2D + index * 0.25D;
        double headY = target.getBoundingBox().maxY;
        double yOffset = 4.4D + index * 0.38D;
        Vec3 start = new Vec3(center.x + Math.cos(angle) * radius,
                headY + yOffset,
                Math.sin(angle) * radius);
        start = new Vec3(start.x, start.y, center.z + start.z);
        Vec3 direction = center.subtract(start).normalize();

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, level);
        sword.setPos(start.x, start.y, start.z);
        sword.setDamage(0.0D);
        sword.setOwner(user);
        sword.setShooter(user);
        sword.setColor(SWORD_COLOR);
        sword.setRoll(index * 90.0F + 45.0F);
        sword.setDelay(0);
        sword.getPersistentData().putBoolean(VISUAL_SWORD, true);
        sword.shoot(direction.x, direction.y, direction.z, 1.55F, 0.0F);
        level.addFreshEntity(sword);
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.34F, 1.25F + index * 0.11F);
    }

    private static void applySealDamage(ServerLevel level, LivingEntity user,
            LivingEntity target, double attackDamage) {
        if (!target.isAlive() || !canAffect(user, target)) {
            return;
        }
        boolean applied = target.hurt(sealDamage(level, user), (float) attackDamage);
        if (!applied) {
            return;
        }
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.9F, 0.72F);
    }

    private static void impact(ServerLevel level, LivingEntity user,
            LivingEntity target, Vec3 center, double attackDamage) {
        if (target.isAlive() && canAffect(user, target)) {
            float damage = (float) (attackDamage * 1.5D
                    + target.getMaxHealth() * MAX_HEALTH_DAMAGE);
            target.hurt(boundaryDamage(level, user), damage);
            center = target.getBoundingBox().getCenter();
        }

        final LivingEntity primary = target;
        AABB area = new AABB(center, center).inflate(IMPACT_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != primary && canAffect(user, entity))) {
            nearby.hurt(boundaryDamage(level, user), (float) attackDamage);
        }

        level.playSound(null, net.minecraft.core.BlockPos.containing(center),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.95F, 0.58F);
        level.playSound(null, net.minecraft.core.BlockPos.containing(center),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.25F, 0.56F);
    }

    private static void beginSuppressionFlame(ServerLevel level, LivingEntity user,
            LivingEntity target, Vec3 center) {
        if (!target.isAlive() || !canAffect(user, target)) return;

        if (!(target instanceof MikageEntity)
                && !target.getType().is(Tags.EntityTypes.BOSSES)) {
            int duration = target instanceof Player ? 12 : SUPPRESSION_TICKS;
            int amplifier = target instanceof Player ? 0 : 4;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    duration, amplifier, false, false, true));
        }

        float scale = Mth.clamp(target.getBbHeight() / 1.8F, 0.82F, 2.15F);
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME,
                center.x, center.y, center.z, center.x, center.y, center.z,
                user.getYRot(), scale, user.getId(), target.getId(),
                FLAME_DURATION_TICKS, level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.75F, 0.68F);
    }

    private static void applyFlamePulse(ServerLevel level, LivingEntity user,
            LivingEntity target, double attackDamage) {
        if (!target.isAlive() || !canAffect(user, target)) return;
        float pulseDamage = (float) (attackDamage
                * FLAME_TOTAL_ATTACK_DAMAGE / FLAME_PULSES)
                + target.getMaxHealth() * FLAME_MAX_HEALTH_DAMAGE_PER_PULSE;
        target.hurt(flameDamage(level, user), pulseDamage);
    }

    private static DamageSource boundaryDamage(ServerLevel level, LivingEntity user) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BOUNDARY_FLASH), user);
    }

    private static DamageSource sealDamage(ServerLevel level, LivingEntity user) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BOUNDARY_SEAL), user);
    }

    private static DamageSource flameDamage(ServerLevel level, LivingEntity user) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BOUNDARY_FLAME), user);
    }

    /**
     * Graduation effects only seek genuine opponents. Passive wildlife, villagers,
     * golems, pets, teammates and protected players are ignored by both lock-on and AoE.
     */
    private static boolean canAffect(LivingEntity user, LivingEntity target) {
        if (target == user || !target.isAlive() || user.isAlliedTo(target)
                || target.isAlliedTo(user)) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator()
                    && user instanceof Player attackingPlayer
                    && attackingPlayer.canHarmPlayer(player);
        }
        if (target instanceof TamableAnimal || target instanceof AbstractVillager
                || target instanceof AbstractGolem || target instanceof WaterAnimal
                || target instanceof AmbientCreature) {
            return false;
        }
        if (target instanceof Enemy) {
            return true;
        }
        if (target instanceof Animal) {
            return false;
        }
        if (target instanceof Mob mob) {
            return mob.getTarget() == user || mob.getLastHurtByMob() == user;
        }
        return target.getLastHurtByMob() == user;
    }

    private static void clearLegacyState(ItemStack blade) {
        if (!blade.hasTag()) {
            return;
        }
        for (String key : LEGACY_TAGS) {
            blade.getTag().remove(key);
        }
    }

    private static final class BoundaryStrike {
        final ResourceKey<net.minecraft.world.level.Level> dimension;
        final UUID userId;
        final UUID targetId;
        final long startedAt;
        final double attackDamage;
        final double orientation;
        Vec3 lastCenter;
        int swordsLaunched;
        boolean sealDamageApplied;
        boolean impactApplied;
        int flamePulsesApplied;

        BoundaryStrike(ResourceKey<net.minecraft.world.level.Level> dimension,
                UUID userId, UUID targetId, long startedAt, double attackDamage,
                double orientation, Vec3 lastCenter) {
            this.dimension = dimension;
            this.userId = userId;
            this.targetId = targetId;
            this.startedAt = startedAt;
            this.attackDamage = attackDamage;
            this.orientation = orientation;
            this.lastCenter = lastCenter;
        }

        boolean tick(ServerLevel level, long now) {
            Entity userEntity = level.getEntity(userId);
            Entity targetEntity = level.getEntity(targetId);
            if (!(userEntity instanceof LivingEntity user)) {
                return false;
            }
            LivingEntity target = targetEntity instanceof LivingEntity living
                    ? living : null;
            int age = (int) (now - startedAt);
            if (target != null) {
                lastCenter = target.getBoundingBox().getCenter();
            }

            while (target != null && swordsLaunched < 4
                    && age >= 1 + swordsLaunched * SWORD_INTERVAL_TICKS) {
                launchVisualSword(level, user, target, swordsLaunched, orientation);
                swordsLaunched++;
            }

            if (!sealDamageApplied && age >= LOCK_TICK) {
                sealDamageApplied = true;
                if (target != null) {
                    applySealDamage(level, user, target, attackDamage);
                }
            }
            if (age < IMPACT_TICK) return true;
            if (!impactApplied) {
                impactApplied = true;
                if (target != null) {
                    impact(level, user, target, lastCenter, attackDamage);
                    beginSuppressionFlame(level, user, target, lastCenter);
                } else {
                    impact(level, user, user, lastCenter, attackDamage);
                }
            }
            while (target != null && flamePulsesApplied < FLAME_PULSES
                    && age >= IMPACT_TICK + 5
                    + flamePulsesApplied * FLAME_INTERVAL_TICKS) {
                applyFlamePulse(level, user, target, attackDamage);
                flamePulsesApplied++;
            }
            return age < IMPACT_TICK + FLAME_DURATION_TICKS;
        }
    }

    private BoundaryForging() {
    }
}
