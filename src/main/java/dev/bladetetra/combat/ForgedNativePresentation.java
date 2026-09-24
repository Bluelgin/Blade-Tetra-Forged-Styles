package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cosmetic reuse of Resharped's native SlashEffect presentation language.
 *
 * <p>Full Sakura/Circle/Void helpers are intentionally not executed as combat
 * callbacks: Sakura/Circle post DoSlashEvent and attach a damaging owner, while
 * Void owns a high-damage terminal burst. Instead this runtime keeps their native
 * entity type, renderer, sound and source geometry, strips combat ownership, and
 * lets the forged executor own every bounded hit.</p>
 */
final class ForgedNativePresentation {
    static final int SAKURA_LIFETIME_TICKS = 10;
    static final int CIRCLE_LIFETIME_TICKS = 10;
    static final int VOID_NATIVE_LIFETIME_TICKS = 36;
    static final int VOID_SAFE_DISCARD_TICKS = 35;

    private static final List<PendingSlash> PENDING_SLASHES = new ArrayList<>();
    private static final List<PendingDiscard> PENDING_DISCARDS = new ArrayList<>();

    static void spawnSakuraCross(ServerPlayer player, Vec3 focus,
            int color, float size) {
        // SakuraEnd source rolls: 22.5 and 180 - 22.5.
        spawnSlash(player.serverLevel(), focus, player.getYRot(),
                22.5F, color, size, SAKURA_LIFETIME_TICKS, false, false);
        scheduleSlash(player.serverLevel(), focus, player.getYRot(),
                157.5F, color, size, SAKURA_LIFETIME_TICKS,
                false, true, 2);
    }

    static void spawnCircle(ServerPlayer player, int color, float size) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position()
                .add(0.0D, player.getEyeHeight() * 0.75D, 0.0D)
                .add(player.getLookAngle().scale(0.3D));
        // Exact Resharped CircleSlash yRot sequence: 180, 90, 0, -90.
        float[] rotations = {180.0F, 90.0F, 0.0F, -90.0F};
        for (int i = 0; i < rotations.length; i++) {
            float yRot = player.getYRot() - 22.5F + rotations[i];
            if (i == 0) {
                spawnSlash(level, pos, yRot, 0.0F, color, size,
                        CIRCLE_LIFETIME_TICKS, false, false);
            } else {
                scheduleSlash(level, pos, yRot, 0.0F, color, size,
                        CIRCLE_LIFETIME_TICKS, false, false, i);
            }
        }
    }

    static void spawnVoid(ServerPlayer player, int color) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position()
                .add(0.0D, player.getEyeHeight() * 0.75D, 0.0D)
                .add(player.getLookAngle().scale(0.3D));

        // This is Resharped's actual Void Slash anonymous EntitySlashEffect:
        // renderer/sound/lifetime semantics are native. Combat ownership is
        // removed before the entity receives its first tick.
        EntitySlashEffect effect = AttackManager.newVoidSlashEffect(player, pos);
        effect.setColor(color);
        effect.setDamage(0.0D);
        effect.setKnockBack(KnockBacks.cancel);
        effect.setNoClip(true);
        effect.setCycleHit(false);
        effect.setLifetime(VOID_NATIVE_LIFETIME_TICKS);
        effect.setShooter(null);
        LegacyFusionCombatSupport.markVisualOnly(effect);
        level.addFreshEntity(effect);

        PENDING_DISCARDS.add(new PendingDiscard(
                level.dimension(), effect.getUUID(),
                level.getGameTime() + VOID_SAFE_DISCARD_TICKS));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var slashIterator = PENDING_SLASHES.iterator();
        while (slashIterator.hasNext()) {
            PendingSlash pending = slashIterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                slashIterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.spawnAt()) {
                continue;
            }
            slashIterator.remove();
            spawnSlash(level, pending.position(), pending.yRot(), pending.roll(),
                    pending.color(), pending.size(), pending.lifetime(),
                    pending.mute(), pending.critical());
        }

        var discardIterator = PENDING_DISCARDS.iterator();
        while (discardIterator.hasNext()) {
            PendingDiscard pending = discardIterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                discardIterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.discardAt()) {
                continue;
            }
            discardIterator.remove();
            Entity entity = level.getEntity(pending.entityId());
            if (entity instanceof EntitySlashEffect
                    && entity.getPersistentData().getBoolean(
                            BladeTechniqueHandler.TECHNIQUE_ENTITY)) {
                entity.discard();
            }
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING_SLASHES.removeIf(
                pending -> pending.dimension().equals(level.dimension()));
        PENDING_DISCARDS.removeIf(
                pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING_SLASHES.clear();
        PENDING_DISCARDS.clear();
    }

    private static void scheduleSlash(ServerLevel level, Vec3 position,
            float yRot, float roll, int color, float size, int lifetime,
            boolean mute, boolean critical, int delayTicks) {
        PENDING_SLASHES.add(new PendingSlash(
                level.dimension(), position, yRot, roll, color, size, lifetime,
                mute, critical, level.getGameTime() + Math.max(1, delayTicks)));
    }

    private static void spawnSlash(ServerLevel level, Vec3 position,
            float yRot, float roll, int color, float size, int lifetime,
            boolean mute, boolean critical) {
        EntitySlashEffect effect = new EntitySlashEffect(
                SlashBlade.RegistryEvents.SlashEffect, level);
        effect.setPos(position.x, position.y, position.z);
        effect.setRotationRoll(roll);
        effect.setYRot(yRot);
        effect.setXRot(0.0F);
        effect.setColor(color);
        effect.setMute(mute);
        effect.setIsCritical(critical);
        effect.setDamage(0.0D);
        effect.setKnockBack(KnockBacks.cancel);
        effect.setBaseSize(size);
        effect.setLifetime(lifetime);
        effect.setNoClip(true);
        effect.setCycleHit(false);
        effect.setShooter(null);
        LegacyFusionCombatSupport.markVisualOnly(effect);
        level.addFreshEntity(effect);
    }

    private record PendingSlash(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yRot,
            float roll,
            int color,
            float size,
            int lifetime,
            boolean mute,
            boolean critical,
            long spawnAt) {
    }

    private record PendingDiscard(
            ResourceKey<Level> dimension,
            UUID entityId,
            long discardAt) {
    }

    private ForgedNativePresentation() {
    }
}
