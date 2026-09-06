package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * One mutually-exclusive Tetra blade technique, expressed through SlashBlade's
 * native entities. Secondary hits are tagged so they cannot recursively feed
 * technique or awakened-blade mechanics.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeTechniqueHandler {
    public static final String TECHNIQUE_ENTITY = "blade_tetra_technique_entity";
    private static final String VISUAL_ONLY = "blade_tetra_technique_visual";
    private static final String COOLDOWN_PREFIX = "blade_tetra_technique_cooldown_";

    public static final ResourceKey<DamageType> TECHNIQUE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID, "blade_technique"));

    private static final int WIND_CUT_COOLDOWN = 50;
    private static final int FLYING_SWALLOW_COOLDOWN = 80;
    private static final int FULL_MOON_COOLDOWN = 100;
    private static final int ZANSHIN_COOLDOWN = 60;
    private static final List<PendingZanshin> PENDING_ZANSHIN = new ArrayList<>();

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        LivingEntity user = event.getUser();
        LivingEntity target = event.getTarget();
        ItemStack blade = event.getBlade();
        if (!(user instanceof ServerPlayer player)
                || !(user.level() instanceof ServerLevel level)
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || !canAffect(user, target)) {
            return;
        }

        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        long now = level.getGameTime();
        Technique technique = resolve(blade);
        switch (technique) {
            case WIND_CUT -> {
                if (user.onGround() && isGroundFinisher(combo)
                        && consumeCooldown(blade, technique, now, WIND_CUT_COOLDOWN)) {
                    spawnWindCut(level, player, blade);
                }
            }
            case FLYING_SWALLOW -> {
                if (isAerialFinisher(combo)
                        && consumeCooldown(blade, technique, now, FLYING_SWALLOW_COOLDOWN)) {
                    spawnFlyingSwallow(level, player, target, blade);
                }
            }
            case ZANSHIN -> {
                if (isGroundFinisher(combo)
                        && consumeCooldown(blade, technique, now, ZANSHIN_COOLDOWN)) {
                    scheduleZanshin(level, player, target, blade, now);
                }
            }
            default -> {
            }
        }
    }

    /** Observes the guard cancellation performed by SlashBlade and keeps only its just window. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPreciseGuard(LivingAttackEvent event) {
        LivingEntity defender = event.getEntity();
        ItemStack blade = defender.getMainHandItem();
        if (!event.isCanceled()
                || !(defender instanceof ServerPlayer player)
                || !(defender.level() instanceof ServerLevel level)
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || resolve(blade) != Technique.FULL_MOON
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || !isJustGuard(defender)) {
            return;
        }

        long now = level.getGameTime();
        if (!consumeCooldown(blade, Technique.FULL_MOON, now, FULL_MOON_COOLDOWN)) {
            return;
        }
        releaseFullMoon(level, player, event.getSource().getEntity(), blade);
    }

    /** Prevents visual-only cuts and allied native projectiles from hurting or staggering. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectTechniqueTargets(LivingAttackEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null || !isTechniqueEntity(direct)) {
            return;
        }
        if (direct.getPersistentData().getBoolean(VISUAL_ONLY)) {
            event.setCanceled(true);
            return;
        }
        LivingEntity owner = techniqueOwner(direct);
        if (owner == null || !canAffect(owner, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_ZANSHIN.isEmpty()) {
            return;
        }
        Iterator<PendingZanshin> iterator = PENDING_ZANSHIN.iterator();
        while (iterator.hasNext()) {
            PendingZanshin pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null || level.getGameTime() < pending.dueTick()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            Entity entity = level.getEntity(pending.targetId());
            if (player == null || player.level() != level
                    || !(entity instanceof LivingEntity target)
                    || !target.isAlive() || !canAffect(player, target)
                    || player.distanceToSqr(target) > 32.0D * 32.0D
                    || resolve(player.getMainHandItem()) != Technique.ZANSHIN) {
                continue;
            }
            executeZanshin(level, player, target, pending);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING_ZANSHIN.removeIf(pending -> pending.dimension().equals(level.dimension()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_ZANSHIN.clear();
    }

    public static boolean isTechniqueEntity(Entity entity) {
        return entity.getPersistentData().getBoolean(TECHNIQUE_ENTITY);
    }

    public static boolean isTechniqueDamage(DamageSource source) {
        return source.is(TECHNIQUE_DAMAGE)
                || (source.getDirectEntity() != null
                && isTechniqueEntity(source.getDirectEntity()));
    }

    private static void spawnWindCut(
            ServerLevel level,
            ServerPlayer player,
            ItemStack blade) {
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition()
                .add(direction.scale(0.85D))
                .add(0.0D, -0.42D, 0.0D);
        EntityDrive drive = new EntityDrive(SlashBlade.RegistryEvents.Drive, level);
        drive.setPos(start.x, start.y, start.z);
        drive.setOwner(player);
        drive.setShooter(player);
        drive.setDamage(techniqueDamage(player, 0.30D, 6.0D));
        drive.setColor(MaterialSlashEffectResolver.resolve(blade).color());
        drive.setKnockBack(KnockBacks.cancel);
        drive.setBaseSize(0.82F);
        drive.setLifetime(18.0F);
        drive.setSpeed(1.35F);
        drive.getPersistentData().putBoolean(TECHNIQUE_ENTITY, true);
        drive.shoot(direction.x, direction.y, direction.z, 1.35F, 0.0F);
        level.addFreshEntity(drive);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.62F, 1.34F);
    }

    private static void spawnFlyingSwallow(
            ServerLevel level,
            ServerPlayer player,
            LivingEntity target,
            ItemStack blade) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        int color = MaterialSlashEffectResolver.resolve(blade).color();
        double damage = techniqueDamage(player, 0.18D, 4.0D);
        for (int index = 0; index < 2; index++) {
            double sideOffset = index == 0 ? -0.85D : 0.85D;
            Vec3 start = center.add(side.scale(sideOffset)).add(0.0D, 3.2D, 0.0D);
            Vec3 direction = center.subtract(start).normalize();
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                    SlashBlade.RegistryEvents.SummonedSword, level);
            sword.setPos(start.x, start.y, start.z);
            sword.setOwner(player);
            sword.setShooter(player);
            sword.setHitEntity(target);
            sword.setDamage(damage);
            sword.setColor(color);
            sword.setRoll(index == 0 ? -28.0F : 28.0F);
            sword.setDelay(6 + index * 3);
            sword.setNoClip(true);
            sword.getPersistentData().putBoolean(TECHNIQUE_ENTITY, true);
            sword.shoot(direction.x, direction.y, direction.z, 1.65F, 0.0F);
            level.addFreshEntity(sword);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.42F, 1.48F);
    }

    private static void releaseFullMoon(
            ServerLevel level,
            ServerPlayer player,
            Entity originalAttacker,
            ItemStack blade) {
        int color = MaterialSlashEffectResolver.resolve(blade).color();
        spawnVisualSlash(player, player.position().add(0.0D, 0.85D, 0.0D),
                0.0F, color, 1.75F, 12);
        spawnVisualSlash(player, player.position().add(0.0D, 0.85D, 0.0D),
                90.0F, color, 1.75F, 12);

        float damage = (float) techniqueDamage(player, 0.25D, 5.0D);
        AABB area = player.getBoundingBox().inflate(3.25D, 1.5D, 3.25D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player
                        && (entity == originalAttacker || entity instanceof Enemy)
                        && canAffect(player, entity))) {
            target.hurt(techniqueDamageSource(level, player), damage);
            Vec3 away = target.position().subtract(player.position());
            if (away.lengthSqr() > 1.0E-5D) {
                target.push(away.x * 0.12D, 0.08D, away.z * 0.12D);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_HIT_GROUND,
                SoundSource.PLAYERS, 0.88F, 1.22F);
    }

    private static void scheduleZanshin(
            ServerLevel level,
            ServerPlayer player,
            LivingEntity target,
            ItemStack blade,
            long now) {
        PENDING_ZANSHIN.add(new PendingZanshin(
                level.dimension(), player.getUUID(), target.getUUID(), now + 12L,
                (float) techniqueDamage(player, 0.30D, 6.0D),
                MaterialSlashEffectResolver.resolve(blade).color(), player.getYRot()));
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS, 0.24F, 1.72F);
    }

    private static void executeZanshin(
            ServerLevel level,
            ServerPlayer player,
            LivingEntity target,
            PendingZanshin pending) {
        spawnVisualSlash(player,
                target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D),
                pending.yaw() + 180.0F, pending.color(),
                Math.max(1.0F, Math.min(2.8F, target.getBbHeight() * 0.95F)), 10);
        target.hurt(techniqueDamageSource(level, player), pending.damage());
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.72F, 0.92F);
    }

    private static void spawnVisualSlash(
            LivingEntity user,
            Vec3 position,
            float yaw,
            int color,
            float size,
            int lifetime) {
        EntitySlashEffect slash = AttackManager.doSlash(user, yaw, true, false, 0.0D);
        if (slash == null) {
            return;
        }
        slash.setDamage(0.0D);
        slash.setMute(true);
        slash.setIndirect(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setColor(color);
        slash.setBaseSize(size);
        slash.setLifetime(lifetime);
        slash.setPos(position.x, position.y, position.z);
        slash.getPersistentData().putBoolean(TECHNIQUE_ENTITY, true);
        slash.getPersistentData().putBoolean(VISUAL_ONLY, true);
    }

    private static boolean isJustGuard(LivingEntity defender) {
        long lastSneak = defender.getCapability(CapabilityInputState.INPUT_STATE)
                .map(state -> state.getLastPressTime(InputCommand.SNEAK))
                .orElse(Long.MIN_VALUE / 2L);
        long elapsed = defender.level().getGameTime() - lastSneak;
        int window = 5 + EnchantmentHelper.getEnchantmentLevel(
                Enchantments.SOUL_SPEED, defender);
        return elapsed >= 0L && elapsed < window;
    }

    private static boolean consumeCooldown(
            ItemStack blade,
            Technique technique,
            long now,
            int duration) {
        CompoundTag tag = blade.getOrCreateTag();
        String key = COOLDOWN_PREFIX + technique.id;
        if (tag.getLong(key) > now) {
            return false;
        }
        tag.putLong(key, now + duration);
        return true;
    }

    private static Technique resolve(ItemStack blade) {
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return Technique.NONE;
        }
        for (Technique technique : Technique.values()) {
            if (technique != Technique.NONE
                    && ForgingImprovements.has(blade,
                    ModularSlashBladeItem.BLADE_SLOT, technique.improvement)) {
                return technique;
            }
        }
        return Technique.NONE;
    }

    private static boolean isGroundFinisher(ResourceLocation combo) {
        return ComboStateRegistry.COMBO_A4.getId().equals(combo)
                || ComboStateRegistry.COMBO_A5.getId().equals(combo)
                || ComboStateRegistry.COMBO_B7.getId().equals(combo)
                || ModComboStates.isIaidoFinish(combo)
                || ModComboStates.isDangakuCleave(combo);
    }

    private static boolean isAerialFinisher(ResourceLocation combo) {
        return ComboStateRegistry.AERIAL_RAVE_A3.getId().equals(combo)
                || ComboStateRegistry.AERIAL_RAVE_B4.getId().equals(combo)
                || ComboStateRegistry.AERIAL_CLEAVE_LANDING.getId().equals(combo);
    }

    private static double techniqueDamage(
            LivingEntity player,
            double ratio,
            double cap) {
        return Math.max(0.5D, Math.min(cap,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ratio));
    }

    private static DamageSource techniqueDamageSource(
            ServerLevel level,
            LivingEntity user) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(TECHNIQUE_DAMAGE), null, user);
    }

    private static LivingEntity techniqueOwner(Entity direct) {
        if (direct instanceof EntityAbstractSummonedSword sword
                && sword.getShooter() instanceof LivingEntity owner) {
            return owner;
        }
        if (direct instanceof EntitySlashEffect slash
                && slash.getShooter() instanceof LivingEntity owner) {
            return owner;
        }
        return direct instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner ? owner : null;
    }

    private static boolean canAffect(LivingEntity owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || owner.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof TamableAnimal tame && tame.isOwnedBy(owner)) {
            return false;
        }
        if (owner instanceof Player player && target instanceof Player other
                && !player.canHarmPlayer(other)) {
            return false;
        }
        return true;
    }

    private enum Technique {
        NONE("none", ""),
        WIND_CUT("wind_cut", ForgingImprovements.TECHNIQUE_WIND_CUT),
        FLYING_SWALLOW("flying_swallow", ForgingImprovements.TECHNIQUE_FLYING_SWALLOW),
        FULL_MOON("full_moon", ForgingImprovements.TECHNIQUE_FULL_MOON),
        ZANSHIN("zanshin", ForgingImprovements.TECHNIQUE_ZANSHIN);

        private final String id;
        private final String improvement;

        Technique(String id, String improvement) {
            this.id = id;
            this.improvement = improvement;
        }
    }

    private record PendingZanshin(
            ResourceKey<net.minecraft.world.level.Level> dimension,
            UUID playerId,
            UUID targetId,
            long dueTick,
            float damage,
            int color,
            float yaw) {
    }

    public static void appendTooltip(ItemStack blade, List<Component> tooltip) {
        Technique technique = resolve(blade);
        if (technique == Technique.NONE) return;
        tooltip.add(Component.translatable("tooltip.blade_tetra.technique",
                        Component.translatable("item.tetra.scroll.blade_tetra."
                                + technique.id + ".name"))
                .withStyle(ChatFormatting.GREEN));
    }

    private BladeTechniqueHandler() {
    }
}
