package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.slasharts.WaveEdge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Tsukumo Cross is intentionally assembled from SlashBlade's own SA projectiles.
 * Wave Edge supplies the vertical wave and Drive Horizontal supplies the crossing
 * phantom blade. This handler only sequences the two source techniques and adds
 * a cosmetic cross-close cue; it never re-implements their hit or damage logic.
 */
final class TsukumoCrossNativeHandler {
    static final long VERTICAL_DELAY = 2L;
    static final long HORIZONTAL_DELAY = 6L;
    static final long CLOSE_DELAY = 9L;

    // Keep the source SA parameters recognizable. EntityDrive performs the real
    // owner-scaled damage and hit processing inside SlashBlade itself.
    static final double WAVE_EDGE_DAMAGE = 0.40D;
    static final double DRIVE_HORIZONTAL_DAMAGE = 1.50D;

    private static final int WAVE_COLOR = 0xB7C9B2;
    private static final int DRIVE_COLOR = 0xE7D7B2;
    private static final List<Cast> CASTS = new ArrayList<>();

    static void onSlashArt(ServerPlayer player, ItemStack blade) {
        if (LegacyFusion.active(blade) != LegacyFusion.AGITO_SAYA_TUKUMO_HILT) {
            return;
        }

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 direction = horizontalDirection(player);
        Vec3 center = player.position().add(0.0D, 0.9D, 0.0D)
                .add(direction.scale(4.8D));

        CASTS.removeIf(cast -> cast.playerId.equals(player.getUUID()));
        CASTS.add(new Cast(level.dimension(), player.getUUID(), center,
                now + VERTICAL_DELAY, now + HORIZONTAL_DELAY,
                now + CLOSE_DELAY));

        level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 0.9D, player.getZ(), 8,
                0.24D, 0.30D, 0.24D, 0.01D);
        level.playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.45F, 1.15F);
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<Cast> iterator = CASTS.iterator();
        while (iterator.hasNext()) {
            Cast cast = iterator.next();
            ServerLevel level = event.getServer().getLevel(cast.dimension);
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(cast.playerId);
            if (level == null || player == null || player.level() != level
                    || !player.isAlive()
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.AGITO_SAYA_TUKUMO_HILT) {
                iterator.remove();
                continue;
            }

            long now = level.getGameTime();
            if (cast.stage == 0 && now >= cast.verticalTick) {
                fireWaveEdge(level, player);
                cast.stage = 1;
            }
            if (cast.stage == 1 && now >= cast.horizontalTick) {
                fireHorizontalDrive(level, player, cast);
                cast.stage = 2;
            }
            if (cast.stage == 2 && now >= cast.closeTick) {
                renderCrossClose(level, player, cast.closeCenter);
                iterator.remove();
            }
        }
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        CASTS.removeIf(cast -> cast.dimension.equals(dimension));
    }

    static void clear() {
        CASTS.clear();
    }

    private static void fireWaveEdge(ServerLevel level, ServerPlayer player) {
        // Exact native Wave Edge projectile language: five EntityDrive waves
        // with randomized 0.2..1.0 speed and the source SA's 0.4 damage factor.
        WaveEdge.doSlash(player, 90.0F, 20, Vec3.ZERO, false,
                WAVE_EDGE_DAMAGE, 0.2F, 1.0F, 4);
        LegacyFusionCombatSupport.spawnVisualSlash(player,
                player.position().add(0.0D, 0.9D, 0.0D)
                        .add(player.getLookAngle().scale(1.0D)),
                player.getYRot(), 90.0F, WAVE_COLOR, 1.15F, 5);
        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.62F, 0.96F);
    }

    private static void fireHorizontalDrive(ServerLevel level,
            ServerPlayer player, Cast cast) {
        // Exact native Drive Horizontal projectile language: EntityDrive owns
        // hit detection, i-frames, rank/refine scaling and enchant callbacks.
        Drive.doSlash(player, 0.0F, 10, Vec3.ZERO, false,
                DRIVE_HORIZONTAL_DAMAGE, 2.0F);

        Vec3 direction = horizontalDirection(player);
        cast.closeCenter = player.position().add(0.0D, 0.9D, 0.0D)
                .add(direction.scale(4.8D));
        LegacyFusionCombatSupport.spawnVisualSlash(player,
                player.position().add(0.0D, 0.9D, 0.0D)
                        .add(direction.scale(1.1D)),
                player.getYRot(), 0.0F, DRIVE_COLOR, 1.35F, 6);
        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.72F, 1.16F);
    }

    private static void renderCrossClose(ServerLevel level,
            ServerPlayer player, Vec3 center) {
        // The close is deliberately cosmetic. All actual damage has already
        // been authored by SlashBlade's Wave Edge / Drive entities.
        LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                player.getYRot(), 90.0F, 0xF2EADB, 1.35F, 6);
        LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                player.getYRot(), 0.0F, 0xF2EADB, 1.35F, 6);
        level.sendParticles(ParticleTypes.CRIT,
                center.x, center.y, center.z, 10,
                0.40D, 0.40D, 0.40D, 0.06D);
        level.sendParticles(ParticleTypes.ENCHANT,
                center.x, center.y, center.z, 8,
                0.70D, 0.40D, 0.70D, 0.025D);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.72F, 1.45F);
    }

    private static Vec3 horizontalDirection(ServerPlayer player) {
        Vec3 direction = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-5D) {
            double yaw = Math.toRadians(player.getYRot());
            direction = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return direction.normalize();
    }

    private static final class Cast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private Vec3 closeCenter;
        private final long verticalTick;
        private final long horizontalTick;
        private final long closeTick;
        private int stage;

        private Cast(ResourceKey<Level> dimension, UUID playerId,
                Vec3 closeCenter, long verticalTick, long horizontalTick,
                long closeTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.closeCenter = closeCenter;
            this.verticalTick = verticalTick;
            this.horizontalTick = horizontalTick;
            this.closeTick = closeTick;
        }
    }

    private TsukumoCrossNativeHandler() {
    }
}
