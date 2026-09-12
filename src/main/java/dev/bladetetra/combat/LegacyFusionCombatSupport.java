package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared server-authoritative helpers for legacy fusion combat abilities. */
final class LegacyFusionCombatSupport {
    private static final String VISUAL_ONLY = "blade_tetra_technique_visual";

    static boolean canAffect(LivingEntity owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || owner.isAlliedTo(target)) {
            return false;
        }
        if (target instanceof TamableAnimal tame && tame.isOwnedBy(owner)) {
            return false;
        }
        return !(owner instanceof Player player && target instanceof Player other)
                || player.canHarmPlayer(other);
    }

    /**
     * Lets one authored rapid hit bypass the vanilla hurt window without exposing
     * that cleared window to unrelated attacks after this call returns.
     */
    static boolean hurtPreservingIFrames(ServerLevel level, ServerPlayer player,
            LivingEntity target, float damage) {
        if (damage <= 0.0F || !canAffect(player, target)) {
            return false;
        }
        int previousInvulnerableTime = target.invulnerableTime;
        try {
            target.invulnerableTime = 0;
            return SoulLegacyDamageGuard.apply(() -> target.hurt(
                    level.damageSources().playerAttack(player), damage));
        } finally {
            target.invulnerableTime = Math.max(
                    previousInvulnerableTime, target.invulnerableTime);
        }
    }

    /**
     * Spawns SlashBlade's native slash mesh as a cosmetic entity only. Clearing
     * its shooter is intentional: EntitySlashEffect's indirect attack path scales
     * its stored damage by the player's attack attribute and can even turn a
     * nominal zero-damage slash into a Rank-damage hit.
     */
    static void spawnVisualSlash(LivingEntity user, Vec3 position,
            float yaw, float roll, int color, float size, int lifetime) {
        EntitySlashEffect slash = AttackManager.doSlash(user, yaw,
                true, false, 0.0D);
        if (slash == null) {
            return;
        }
        slash.setDamage(0.0D);
        slash.setMute(true);
        slash.setNoClip(true);
        slash.setCycleHit(false);
        slash.setKnockBack(KnockBacks.cancel);
        slash.setColor(color);
        slash.setBaseSize(size);
        slash.setLifetime(lifetime);
        slash.setRotationRoll(roll);
        slash.setPos(position.x, position.y, position.z);
        slash.setYRot(yaw);
        slash.setShooter(null);
        slash.getPersistentData().putBoolean(
                BladeTechniqueHandler.TECHNIQUE_ENTITY, true);
        slash.getPersistentData().putBoolean(VISUAL_ONLY, true);
    }

    private LegacyFusionCombatSupport() {
    }
}
