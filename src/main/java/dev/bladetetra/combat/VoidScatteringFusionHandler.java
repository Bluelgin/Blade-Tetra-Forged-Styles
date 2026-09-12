package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.VoidScatteringVfxPacket;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sange saya + Yamato hilt: an endgame defensive domain that converts hostile
 * pressure into bounded void charge and returns it as authored summoned swords.
 */
final class VoidScatteringFusionHandler {
    static final int DOMAIN_DURATION_TICKS = 200;
    static final int DOMAIN_COOLDOWN_TICKS = 400;
    static final int MAX_STORED_SWORDS = 6;
    static final int MAX_VOID_CHARGE = 12;
    static final int VOID_CHARGE_PER_SWORD = 2;
    static final int AUTO_BREAK_CAPTURE_COUNT = 5;
    static final int SOURCE_CAPTURE_INTERVAL_TICKS = 8;
    static final int RESIDUAL_DURATION_TICKS = 5;
    static final int RESIDUAL_COOLDOWN_TICKS = 36;
    static final int RETURN_DAMAGE_MAX_ATTEMPTS = 2;

    private static final double DOMAIN_PROJECTILE_RADIUS = 3.25D;
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
    private static final List<PendingReturn> PENDING_RETURNS = new ArrayList<>();

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
            Vec3 impact = domainImpactPoint(player, projectile.position());
            if (capture(player, domain, source, event.getAmount(),
                    globalGameTime(player), impact)) {
                captureVisual(player.serverLevel(), impact);
            }
            return;
        }

        ResidualState residual = RESIDUALS.get(player.getUUID());
        if (residual != null && validResidualPlayer(player, residual)) {
            event.setCanceled(true);
            projectile.discard();
            RESIDUALS.remove(player.getUUID());
            scheduleReturn(player, source.getUUID(),
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
            captureVisual(player.serverLevel(), projectile.position());
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

        float incoming = event.getAmount();
        event.setAmount(VoidScatteringBalance.reducedDamage(incoming));
        Vec3 impact = domainImpactPoint(player, source.position());
        if (capture(player, domain, source, incoming, globalGameTime(player), impact)) {
            captureVisual(player.serverLevel(), impact);
        }
    }

    static void tick(TickEvent.ServerTickEvent event) {
        for (DomainState domain : new ArrayList<>(DOMAINS.values())) {
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(domain.playerId());
            if (player == null) {
                DOMAINS.remove(domain.playerId());
                continue;
            }
            if (!validDomainPlayer(player, domain) || !player.isAlive()) {
                cancelDomain(player);
                continue;
            }

            if (domain.autoRelease()) {
                releaseDomain(player, domain, true);
                continue;
            }

            long now = globalGameTime(player);
            if (now >= domain.endTick()) {
                releaseDomain(player, domain);
                continue;
            }

            interceptDomainProjectiles(player, domain, now);
            if (domain.autoRelease()) {
                releaseDomain(player, domain, true);
                continue;
            }
            renderDomain(player, now);
        }

        for (ResidualState residual : new ArrayList<>(RESIDUALS.values())) {
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(residual.playerId());
            if (player == null) {
                RESIDUALS.remove(residual.playerId());
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

        tickPendingReturns(event);

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            showReadyNoticeIfNeeded(player);
        }
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        DOMAINS.values().removeIf(state -> state.dimension().equals(dimension));
        RESIDUALS.values().removeIf(state -> state.dimension().equals(dimension));
        PENDING_RETURNS.removeIf(state -> state.dimension.equals(dimension));
    }

    static void clear() {
        DOMAINS.clear();
        RESIDUALS.clear();
        PENDING_RETURNS.clear();
    }

    private static void startDomain(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        int seed = player.getRandom().nextInt();
        DomainState domain = new DomainState(level.dimension(), player.getUUID(),
                now + DOMAIN_DURATION_TICKS, attack, seed);
        DOMAINS.put(player.getUUID(), domain);
        RESIDUALS.remove(player.getUUID());

        setDomainCooldown(player, now + DOMAIN_DURATION_TICKS + DOMAIN_COOLDOWN_TICKS);

        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 2.7D, 0.95D, 2.7D, 0.035D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.2D, player.getZ(),
                12, 2.4D, 0.85D, 2.4D, 0.012D);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.68F, 0.66F);
        sendVfx(player, VoidScatteringVfxPacket.OPEN, 0,
                DOMAIN_DURATION_TICKS, seed);
    }

    private static void releaseDomain(ServerPlayer player, DomainState domain) {
        releaseDomain(player, domain, false);
    }

    private static void releaseDomain(ServerPlayer player, DomainState domain,
            boolean shattered) {
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
                scheduleReturn(player, target.getUUID(), damage, index * 3);
                index++;
            }
        }

        Vec3 center = domainCenter(player);
        float slashScale = shattered ? 3.15F : 2.4F;
        int slashLife = shattered ? 14 : 11;
        LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                player.getYRot(), 90.0F, VOID_COLOR, slashScale, slashLife);
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z, shattered ? 38 : 24,
                shattered ? 2.35D : 1.9D, shattered ? 0.95D : 0.75D,
                shattered ? 2.35D : 1.9D, shattered ? 0.11D : 0.075D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                center.x, center.y, center.z, shattered ? 28 : 18,
                shattered ? 2.0D : 1.7D, shattered ? 0.78D : 0.6D,
                shattered ? 2.0D : 1.7D, shattered ? 0.07D : 0.045D);
        if (shattered) {
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, 0.95F, 0.72F);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, shattered ? 0.96F : 0.82F,
                shattered ? 1.10F : 1.22F);
        sendVfx(player, VoidScatteringVfxPacket.COLLAPSE,
                domain.sources().size(), shattered ? 12 : 8, domain.seed());
    }

    private static void cancelDomain(ServerPlayer player) {
        DomainState removed = DOMAINS.remove(player.getUUID());
        if (removed != null) {
            setDomainCooldown(player, globalGameTime(player) + DOMAIN_COOLDOWN_TICKS);
            sendVfx(player, VoidScatteringVfxPacket.CANCEL,
                    removed.sources().size(), 1, removed.seed());
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
        sendVfx(player, VoidScatteringVfxPacket.RESIDUAL, 0,
                RESIDUAL_DURATION_TICKS, player.getRandom().nextInt());
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
            scheduleReturn(player, target.getUUID(),
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
        }
    }

    private static void interceptDomainProjectiles(ServerPlayer player,
            DomainState domain, long now) {
        ServerLevel level = player.serverLevel();
        Vec3 center = domainCenter(player);
        double radiusSqr = DOMAIN_PROJECTILE_RADIUS * DOMAIN_PROJECTILE_RADIUS;
        AABB area = player.getBoundingBox().inflate(DOMAIN_PROJECTILE_RADIUS);
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, area,
                VoidScatteringFusionHandler::isWhitelistedProjectile);
        for (Projectile projectile : projectiles) {
            if (domain.autoRelease()) {
                break;
            }
            if (projectile.position().distanceToSqr(center) > radiusSqr) {
                continue;
            }
            LivingEntity source = projectileOwner(projectile);
            if (source == null || !LegacyFusionCombatSupport.canAffect(player, source)) {
                continue;
            }
            Vec3 impact = domainImpactPoint(player, projectile.position());
            projectile.discard();
            if (capture(player, domain, source, projectilePressure(projectile), now, impact)) {
                captureVisual(level, impact);
            }
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
            Vec3 impact = projectile.position();
            projectile.discard();
            RESIDUALS.remove(residual.playerId());
            scheduleReturn(player, source.getUUID(),
                    VoidScatteringBalance.residualSwordDamage(residual.attackSnapshot()), 0);
            captureVisual(level, impact);
            return true;
        }
        return false;
    }

    private static boolean capture(ServerPlayer player, DomainState domain,
            LivingEntity source, float incomingDamage, long now, Vec3 impactPoint) {
        long previous = domain.lastCapture().getOrDefault(source.getUUID(), Long.MIN_VALUE / 2L);
        if (now - previous < SOURCE_CAPTURE_INTERVAL_TICKS) {
            return false;
        }
        int gainedCharge = VoidScatteringBalance.voidChargeForIncomingDamage(incomingDamage);
        if (gainedCharge <= 0) {
            return false;
        }
        domain.lastCapture().put(source.getUUID(), now);
        domain.incrementCaptureCount();

        int oldCharge = domain.voidCharge();
        int newCharge = Math.min(MAX_VOID_CHARGE, oldCharge + gainedCharge);
        domain.setVoidCharge(newCharge);
        int newSwordCount = Math.min(MAX_STORED_SWORDS,
                newCharge / VOID_CHARGE_PER_SWORD);
        while (domain.sources().size() < newSwordCount
                && domain.sources().size() < MAX_STORED_SWORDS) {
            domain.sources().add(source.getUUID());
        }

        Vec3 direction = impactPoint.subtract(domainCenter(player));
        if (direction.lengthSqr() > 1.0E-5D) {
            direction = direction.normalize();
        } else {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        }
        sendVfx(player, VoidScatteringVfxPacket.CAPTURE,
                domain.sources().size(),
                Math.max(1, (int) (domain.endTick() - now)), domain.seed(), direction);
        if (shouldAutoRelease(domain.captureCount())) {
            domain.markAutoRelease();
        }
        return true;
    }

    static boolean shouldAutoRelease(int captureCount) {
        return captureCount >= AUTO_BREAK_CAPTURE_COUNT;
    }

    private static float projectilePressure(Projectile projectile) {
        if (projectile instanceof ThrownTrident) {
            return 8.0F;
        }
        if (projectile instanceof SmallFireball) {
            return 5.0F;
        }
        if (projectile instanceof Snowball) {
            return 1.0F;
        }
        if (projectile instanceof AbstractArrow) {
            return 6.0F;
        }
        return 1.0F;
    }

    private static Vec3 domainCenter(ServerPlayer player) {
        return player.position().add(0.0D, 1.0D, 0.0D);
    }

    private static Vec3 domainImpactPoint(ServerPlayer player, Vec3 threatPosition) {
        Vec3 center = domainCenter(player);
        Vec3 delta = threatPosition.subtract(center);
        if (delta.lengthSqr() < 1.0E-5D) {
            delta = player.getLookAngle().scale(-1.0D);
        }
        return center.add(delta.normalize().scale(DOMAIN_PROJECTILE_RADIUS * 0.95D));
    }

    private static void scheduleReturn(ServerPlayer player, UUID targetId,
            float damage, int delay) {
        if (damage <= 0.0F) {
            return;
        }
        PENDING_RETURNS.add(new PendingReturn(player.level().dimension(),
                player.getUUID(), targetId, damage,
                globalGameTime(player) + Math.max(0, delay),
                Math.max(0, delay / 3)));
    }

    private static int launchVisualSword(ServerPlayer player, LivingEntity target,
            int sequence) {
        ServerLevel level = player.serverLevel();
        Vec3 center = target.getBoundingBox().getCenter();
        double angle = Math.toRadians((sequence * 53.0D + player.tickCount * 17.0D) % 360.0D);
        Vec3 start = player.position().add(
                Math.cos(angle) * 1.25D,
                1.35D + (sequence % 2) * 0.18D,
                Math.sin(angle) * 1.25D);
        Vec3 direction = center.subtract(start).normalize();

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, level);
        sword.setPos(start.x, start.y, start.z);
        sword.setOwner(player);
        sword.setShooter(player);
        sword.setHitEntity(target);
        sword.setDamage(0.0D);
        sword.setColor(VOID_COLOR);
        sword.setRoll((float) ((sequence * 53) % 360));
        sword.setDelay(0);
        sword.setNoClip(true);
        LegacyFusionCombatSupport.markVisualOnly(sword);
        sword.shoot(direction.x, direction.y, direction.z, 1.7F, 0.0F);
        level.addFreshEntity(sword);
        return Math.max(2, (int) Math.ceil(start.distanceTo(center) / 1.7D));
    }

    private static void tickPendingReturns(TickEvent.ServerTickEvent event) {
        long now = event.getServer().overworld().getGameTime();
        var iterator = PENDING_RETURNS.iterator();
        while (iterator.hasNext()) {
            PendingReturn pending = iterator.next();
            if (now < pending.dueTick) {
                continue;
            }
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            Entity entity = level == null ? null : level.getEntity(pending.targetId);
            if (player == null || player.level() != level
                    || !(entity instanceof LivingEntity target)
                    || !target.isAlive()
                    || !LegacyFusionCombatSupport.canAffect(player, target)
                    || player.distanceToSqr(target) > RETURN_RANGE * RETURN_RANGE) {
                iterator.remove();
                continue;
            }
            if (!pending.launched) {
                pending.launched = true;
                pending.dueTick = now + launchVisualSword(player, target,
                        pending.sequence);
                continue;
            }

            pending.damageAttempts++;
            boolean damaged = LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, pending.damage);
            if (shouldRetryReturnDamage(damaged, pending.damageAttempts)) {
                pending.dueTick = now + 1L;
                continue;
            }
            if (damaged) {
                Vec3 center = target.getBoundingBox().getCenter();
                LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                        player.getYRot(), 90.0F, VOID_COLOR, 0.92F, 6);
                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                        center.x, center.y, center.z, 5,
                        0.35D, 0.35D, 0.35D, 0.025D);
            }
            iterator.remove();
        }
    }

    static boolean shouldRetryReturnDamage(boolean damaged, int attempts) {
        return !damaged && attempts < RETURN_DAMAGE_MAX_ATTEMPTS;
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
        if (now % 6L != 0L) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                2, 2.85D, 0.9D, 2.85D, 0.01D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                player.getX(), player.getY() + 1.15D, player.getZ(),
                1, 2.55D, 0.75D, 2.55D, 0.008D);
    }

    private static void renderResidual(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(1.45D));
        level.sendParticles(ParticleTypes.PORTAL,
                point.x, point.y, point.z, 2,
                0.16D, 0.34D, 0.16D, 0.02D);
    }

    private static void captureVisual(ServerLevel level, Vec3 impact) {
        level.sendParticles(ParticleTypes.PORTAL,
                impact.x, impact.y, impact.z,
                7, 0.28D, 0.28D, 0.28D, 0.055D);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                impact.x, impact.y, impact.z,
                3, 0.24D, 0.24D, 0.24D, 0.025D);
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.28F, 1.36F);
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
        sendVfx(player, VoidScatteringVfxPacket.READY, 0,
                28, player.getRandom().nextInt());
    }

    private static void sendVfx(ServerPlayer player, int type, int slots,
            int duration, int seed) {
        sendVfx(player, type, slots, duration, seed, Vec3.ZERO);
    }

    private static void sendVfx(ServerPlayer player, int type, int slots,
            int duration, int seed, Vec3 impactDirection) {
        VoidScatteringVfxPacket packet = new VoidScatteringVfxPacket(
                type, player.getId(), slots, duration, seed,
                (float) impactDirection.x, (float) impactDirection.y,
                (float) impactDirection.z);
        for (ServerPlayer viewer : player.serverLevel().players()) {
            if (viewer.distanceToSqr(player) <= 64.0D * 64.0D) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static final class DomainState {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final long endTick;
        private final double attackSnapshot;
        private final int seed;
        private final List<UUID> sources = new ArrayList<>();
        private final Map<UUID, Long> lastCapture = new HashMap<>();
        private int voidCharge;
        private int captureCount;
        private boolean autoRelease;

        private DomainState(ResourceKey<Level> dimension, UUID playerId,
                long endTick, double attackSnapshot, int seed) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.endTick = endTick;
            this.attackSnapshot = attackSnapshot;
            this.seed = seed;
        }

        ResourceKey<Level> dimension() { return dimension; }
        UUID playerId() { return playerId; }
        long endTick() { return endTick; }
        double attackSnapshot() { return attackSnapshot; }
        int seed() { return seed; }
        List<UUID> sources() { return sources; }
        Map<UUID, Long> lastCapture() { return lastCapture; }
        int voidCharge() { return voidCharge; }
        void setVoidCharge(int voidCharge) { this.voidCharge = voidCharge; }
        int captureCount() { return captureCount; }
        void incrementCaptureCount() { captureCount++; }
        boolean autoRelease() { return autoRelease; }
        void markAutoRelease() { autoRelease = true; }
    }

    private record ResidualState(ResourceKey<Level> dimension, UUID playerId,
            long endTick, double attackSnapshot, UUID targetId) {
    }

    private static final class PendingReturn {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final UUID targetId;
        private final float damage;
        private final int sequence;
        private long dueTick;
        private boolean launched;
        private int damageAttempts;

        private PendingReturn(ResourceKey<Level> dimension, UUID playerId,
                UUID targetId, float damage, long dueTick, int sequence) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.damage = damage;
            this.dueTick = dueTick;
            this.sequence = sequence;
        }
    }

    private VoidScatteringFusionHandler() {
    }
}