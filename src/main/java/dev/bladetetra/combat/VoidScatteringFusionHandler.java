package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sange saya + Yamato hilt: an endgame defensive domain that stores hostile
 * pressure and returns it as authored summoned swords instead of reflecting the
 * incoming damage value.
 */
final class VoidScatteringFusionHandler {
    static final int DOMAIN_DURATION_TICKS = 60;
    static final int DOMAIN_COOLDOWN_TICKS = 280;
    static final int MAX_STORED_SWORDS = 5;
    static final int SOURCE_CAPTURE_INTERVAL_TICKS = 8;
    static final int RESIDUAL_DURATION_TICKS = 5;
    static final int RESIDUAL_COOLDOWN_TICKS = 36;

    private static final double DOMAIN_PROJECTILE_RADIUS = 5.0D;
    private static final double RETURN_RANGE = 40.0D;
    private static final double RESIDUAL_RANGE = 4.0D;
    private static final int VOID_COLOR = 0xC8A7FF;

    private static final String DOMAIN_READY_AT =
            "blade_tetra_void_scattering_domain_ready_at";
    private static final String DOMAIN_READY_NOTICE =
            "blade_tetra_void_scattering_ready_notice";
    private static final String RESIDUAL_READY_AT =
            "blade_tetra_void_scattering_residual_ready_at";

    private static final Map<UUID, DomainState> DOMAINS = new HashMap<>();
    private static final Map<UUID, ResidualState> RESIDUALS = new HashMap<>();

    /**
     * @return true when this handler consumed the Slash Art event and vanilla
     * SlashBlade should not charge its normal Proud Soul cost.
     */
    static boolean onSlashArt(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ItemStack blade, ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.SANGE_SAYA_YAMATO_HILT) {
            return false;
        }

        DomainState existing = DOMAINS.get(player.getUUID());
        if (existing != null) {
            releaseDomain(player, existing);
            event.setCanceled(true);
            return true;
        }

        long now = globalGameTime(player);
        if (now < domainReadyAt(player)) {
            event.setCanceled(true);
            if (now >= residualReadyAt(player)) {
                startResidual(player, state, now);
            }
            return true;
        }

        startDomain(player, now);
        return false;
    }

    static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getDirectEntity() instanceof Projectile projectile)
                || !isWhitelistedProjectile(projectile)) {
            return;
        }

        LivingEntity source = projectileOwner(projectile);
        if (source == null || !LegacyFusionCombatSupport.canAffect(player, source)) {
            return;
        }

        DomainState domain = DOMAINS.get(player.getUUID());
        if (domain != null && validDomainPlayer(player, domain)) {
            event.setCanceled(true);
            projectile.discard();
            capture(domain, source, globalGameTime(player));
            captureVisual(player.serverLevel(), player);
            return;
        }

        ResidualState residual = RESIDUALS.get(player.getUUID());
        if (residual != null && validResidualPlayer(player, residual)) {
            event.setCanceled(true);
            projectile.discard();
            RESIDUALS.remove(player.getUUID());
            fireSword(player, source,
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
            captureVisual(player.serverLevel(), player);
        }
    }

    static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DomainState domain = DOMAINS.get(player.getUUID());
        if (domain == null || !validDomainPlayer(player, domain)
                || event.getSource().getDirectEntity() instanceof Projectile
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        Entity causing = event.getSource().getEntity();
        if (!(causing instanceof LivingEntity source)
                || !LegacyFusionCombatSupport.canAffect(player, source)) {
            return;
        }

        event.setAmount(VoidScatteringBalance.reducedDamage(event.getAmount()));
        capture(domain, source, globalGameTime(player));
        captureVisual(player.serverLevel(), player);
    }

    static void tick(TickEvent.ServerTickEvent event) {
        for (DomainState domain : new ArrayList<>(DOMAINS.values())) {
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(domain.playerId());
            if (player == null) {
                continue;
            }
            if (!validDomainPlayer(player, domain) || !player.isAlive()) {
                cancelDomain(player);
                continue;
            }

            long now = globalGameTime(player);
            if (now >= domain.endTick()) {
                releaseDomain(player, domain);
                continue;
            }

            interceptDomainProjectiles(player, domain, now);
            renderDomain(player, now);
        }

        for (ResidualState residual : new ArrayList<>(RESIDUALS.values())) {
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(residual.playerId());
            if (player == null) {
                continue;
            }
            if (!validResidualPlayer(player, residual) || !player.isAlive()) {
                RESIDUALS.remove(residual.playerId());
                continue;
            }

            long now = globalGameTime(player);
            if (interceptResidualProjectile(player, residual)) {
                continue;
            }
            if (now >= residual.endTick()) {
                RESIDUALS.remove(residual.playerId());
                releaseResidualFallback(player, residual);
            } else {
                renderResidual(player);
            }
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            showReadyNoticeIfNeeded(player);
        }
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        DOMAINS.values().removeIf(state -> state.dimension().equals(dimension));
        RESIDUALS.values().removeIf(state -> state.dimension().equals(dimension));
    }

    static void clear() {
        DOMAINS.clear();
        RESIDUALS.clear();
    }

    private static void startDomain(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        DomainState domain = new DomainState(level.dimension(), player.getUUID(),
                now + DOMAIN_DURATION_TICKS, attack);
        DOMAINS.put(player.getUUID(), domain);
        RESIDUALS.remove(player.getUUID());

        // Persist a conservative ready time immediately. If the player logs out
        // mid-domain the field cannot be kept alive, but reconnecting does not
        // erase the intended defensive cooldown.
        setDomainCooldown(player, now + DOMAIN_DURATION_TICKS + DOMAIN_COOLDOWN_TICKS);

        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                36, 2.2D, 0.75D, 2.2D, 0.04D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.2D, player.getZ(),
                18, 1.9D, 0.65D, 1.9D, 0.015D);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.55F, 0.72F);
    }

    private static void releaseDomain(ServerPlayer player, DomainState domain) {
        if (DOMAINS.remove(domain.playerId()) == null) {
            return;
        }
        long now = globalGameTime(player);
        setDomainCooldown(player, now + DOMAIN_COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();
        float damage = VoidScatteringBalance.returnSwordDamage(domain.attackSnapshot());
        int index = 0;
        for (UUID sourceId : domain.sources()) {
            Entity entity = level.getEntity(sourceId);
            if (entity instanceof LivingEntity target
                    && LegacyFusionCombatSupport.canAffect(player, target)
                    && player.distanceToSqr(target) <= RETURN_RANGE * RETURN_RANGE) {
                fireSword(player, target, damage, index * 3);
                index++;
            }
        }

        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                player.getYRot(), 90.0F, VOID_COLOR, 2.4F, 11);
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z, 28,
                1.8D, 0.65D, 1.8D, 0.08D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                center.x, center.y, center.z, 20,
                1.6D, 0.55D, 1.6D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.82F, 1.22F);
    }

    private static void cancelDomain(ServerPlayer player) {
        if (DOMAINS.remove(player.getUUID()) != null) {
            setDomainCooldown(player, globalGameTime(player) + DOMAIN_COOLDOWN_TICKS);
        }
        RESIDUALS.remove(player.getUUID());
    }

    private static void startResidual(ServerPlayer player, ISlashBladeState state,
            long now) {
        ServerLevel level = player.serverLevel();
        Entity locked = state.getTargetEntity(level);
        UUID targetId = locked instanceof LivingEntity target
                && LegacyFusionCombatSupport.canAffect(player, target)
                ? target.getUUID() : null;
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        RESIDUALS.put(player.getUUID(), new ResidualState(level.dimension(),
                player.getUUID(), now + RESIDUAL_DURATION_TICKS, attack, targetId));
        player.getPersistentData().putLong(RESIDUAL_READY_AT,
                now + RESIDUAL_COOLDOWN_TICKS);

        Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(1.55D));
        LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                player.getYRot(), 0.0F, VOID_COLOR, 1.15F, 6);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.34F, 1.52F);
    }

    private static void releaseResidualFallback(ServerPlayer player,
            ResidualState residual) {
        if (residual.targetId() == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(residual.targetId());
        if (entity instanceof LivingEntity target
                && LegacyFusionCombatSupport.canAffect(player, target)
                && player.distanceToSqr(target) <= RETURN_RANGE * RETURN_RANGE) {
            fireSword(player, target,
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
        }
    }

    private static void interceptDomainProjectiles(ServerPlayer player,
            DomainState domain, long now) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(DOMAIN_PROJECTILE_RADIUS);
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, area,
                VoidScatteringFusionHandler::isWhitelistedProjectile);
        for (Projectile projectile : projectiles) {
            LivingEntity source = projectileOwner(projectile);
            if (source == null || !LegacyFusionCombatSupport.canAffect(player, source)) {
                continue;
            }
            projectile.discard();
            capture(domain, source, now);
            captureVisual(level, player);
        }
    }

    private static boolean interceptResidualProjectile(ServerPlayer player,
            ResidualState residual) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB area = player.getBoundingBox().inflate(RESIDUAL_RANGE);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area,
                VoidScatteringFusionHandler::isWhitelistedProjectile)) {
            Vec3 delta = projectile.position().subtract(eye);
            double distance = delta.length();
            if (distance > RESIDUAL_RANGE || distance <= 0.001D
                    || look.dot(delta.scale(1.0D / distance)) < 0.62D) {
                continue;
            }
            LivingEntity source = projectileOwner(projectile);
            if (source == null || !LegacyFusionCombatSupport.canAffect(player, source)) {
                continue;
            }
            projectile.discard();
            RESIDUALS.remove(residual.playerId());
            fireSword(player, source,
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
            captureVisual(level, player);
            return true;
        }
        return false;
    }

    private static void capture(DomainState domain, LivingEntity source, long now) {
        if (domain.sources().size() >= MAX_STORED_SWORDS) {
            return;
        }
        long previous = domain.lastCapture().getOrDefault(source.getUUID(), Long.MIN_VALUE / 2L);
        if (now - previous < SOURCE_CAPTURE_INTERVAL_TICKS) {
            return;
        }
        domain.lastCapture().put(source.getUUID(), now);
        domain.sources().add(source.getUUID());
    }

    private static void fireSword(ServerPlayer player, LivingEntity target,
            float damage, int delay) {
        if (damage <= 0.0F || !target.isAlive()
                || player.level() != target.level()
                || !LegacyFusionCombatSupport.canAffect(player, target)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 center = target.getBoundingBox().getCenter();
        double angle = Math.toRadians((delay * 37.0D) % 360.0D);
        Vec3 start = player.position().add(
                Math.cos(angle) * 1.25D,
                1.35D + (delay % 2) * 0.18D,
                Math.sin(angle) * 1.25D);
        Vec3 direction = center.subtract(start).normalize();

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, level);
        sword.setPos(start.x, start.y, start.z);
        sword.setOwner(player);
        sword.setShooter(player);
        sword.setHitEntity(target);
        sword.setDamage(damage);
        sword.setColor(VOID_COLOR);
        sword.setRoll((float) ((delay * 53) % 360));
        sword.setDelay(delay);
        sword.setNoClip(true);
        sword.getPersistentData().putBoolean(BladeTechniqueHandler.TECHNIQUE_ENTITY, true);
        sword.shoot(direction.x, direction.y, direction.z, 1.7F, 0.0F);
        level.addFreshEntity(sword);
    }

    private static boolean validDomainPlayer(ServerPlayer player, DomainState domain) {
        return player.level().dimension().equals(domain.dimension())
                && LegacyFusion.active(player.getMainHandItem())
                == LegacyFusion.SANGE_SAYA_YAMATO_HILT;
    }

    private static boolean validResidualPlayer(ServerPlayer player,
            ResidualState residual) {
        return player.level().dimension().equals(residual.dimension())
                && LegacyFusion.active(player.getMainHandItem())
                == LegacyFusion.SANGE_SAYA_YAMATO_HILT;
    }

    private static boolean isWhitelistedProjectile(Projectile projectile) {
        return projectile instanceof AbstractArrow
                || projectile instanceof ThrownTrident
                || projectile instanceof SmallFireball
                || projectile instanceof Snowball;
    }

    private static LivingEntity projectileOwner(Projectile projectile) {
        return projectile.getOwner() instanceof LivingEntity living ? living : null;
    }

    private static void renderDomain(ServerPlayer player, long now) {
        if (now % 4L != 0L) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                5, 2.35D, 0.7D, 2.35D, 0.015D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.15D, player.getZ(),
                2, 2.1D, 0.6D, 2.1D, 0.01D);
        if (now % 12L == 0L) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            Vec3 crack = player.position().add(
                    Math.cos(angle) * 2.2D,
                    0.75D + player.getRandom().nextDouble() * 1.1D,
                    Math.sin(angle) * 2.2D);
            LegacyFusionCombatSupport.spawnVisualSlash(player, crack,
                    (float) Math.toDegrees(-angle), 90.0F,
                    VOID_COLOR, 0.95F, 7);
        }
    }

    private static void renderResidual(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(1.45D));
        level.sendParticles(ParticleTypes.PORTAL,
                point.x, point.y, point.z, 2,
                0.16D, 0.34D, 0.16D, 0.02D);
    }

    private static void captureVisual(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                10, 0.72D, 0.58D, 0.72D, 0.08D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.15D, player.getZ(),
                5, 0.58D, 0.42D, 0.58D, 0.035D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.28F, 1.36F);
    }

    private static void setDomainCooldown(ServerPlayer player, long readyAt) {
        player.getPersistentData().putLong(DOMAIN_READY_AT, readyAt);
        player.getPersistentData().putBoolean(DOMAIN_READY_NOTICE, true);
    }

    private static long domainReadyAt(ServerPlayer player) {
        return player.getPersistentData().getLong(DOMAIN_READY_AT);
    }

    private static long residualReadyAt(ServerPlayer player) {
        return player.getPersistentData().getLong(RESIDUAL_READY_AT);
    }

    private static long globalGameTime(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    private static void showReadyNoticeIfNeeded(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(DOMAIN_READY_NOTICE)
                || DOMAINS.containsKey(player.getUUID())
                || globalGameTime(player) < domainReadyAt(player)
                || LegacyFusion.active(player.getMainHandItem())
                != LegacyFusion.SANGE_SAYA_YAMATO_HILT) {
            return;
        }
        player.getPersistentData().putBoolean(DOMAIN_READY_NOTICE, false);
        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.void_scattering.ready")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.32F, 1.72F);
        player.serverLevel().sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.15D, player.getZ(),
                5, 0.32D, 0.34D, 0.32D, 0.01D);
    }

    private static final class DomainState {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final long endTick;
        private final double attackSnapshot;
        private final List<UUID> sources = new ArrayList<>();
        private final Map<UUID, Long> lastCapture = new HashMap<>();

        private DomainState(ResourceKey<Level> dimension, UUID playerId,
                long endTick, double attackSnapshot) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.endTick = endTick;
            this.attackSnapshot = attackSnapshot;
        }

        ResourceKey<Level> dimension() { return dimension; }
        UUID playerId() { return playerId; }
        long endTick() { return endTick; }
        double attackSnapshot() { return attackSnapshot; }
        List<UUID> sources() { return sources; }
        Map<UUID, Long> lastCapture() { return lastCapture; }
    }

    private record ResidualState(ResourceKey<Level> dimension, UUID playerId,
            long endTick, double attackSnapshot, UUID targetId) {
    }

    private VoidScatteringFusionHandler() {
    }
}
