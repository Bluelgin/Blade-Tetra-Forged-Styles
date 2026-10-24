package dev.bladetetra.aura;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.registry.ModEntities;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Physical projectile factory. Direction and tilt come from the real slash entity. */
public final class SwordAuraSpawner {
    public static void emit(ServerPlayer player, EntitySlashEffect slash, BladeStyle style) {
        SwordAuraStyleProfile profile = SwordAuraStyleProfile.forStyle(style);
        float damage = SwordAuraDamage.snapshot(player);
        if (!(damage > 0.0F)) return;
        float roll = slash.getRotationRoll();
        Vec3 forward = Vec3.directionFromRotation(player.getXRot(), slash.getYRot()).normalize();
        if (forward.lengthSqr() < 1.0E-6D) return;
        spawn(player, slash.position().add(forward.scale(0.45D)), forward, roll,
                profile, damage, false, slash.getColor());
        if (profile.hasDecoration()) {
            spawn(player, slash.position().add(forward.scale(0.45D))
                            .add(0, profile.additionalHeight(), 0),
                    forward, roll + profile.additionalRoll(), profile, 0.0F, true, slash.getColor());
        }
    }

    private static void spawn(ServerPlayer player, Vec3 position, Vec3 direction,
            float roll, SwordAuraStyleProfile profile, float damage, boolean decoration, int color) {
        SwordAuraDriveEntity drive = new SwordAuraDriveEntity(ModEntities.SWORD_AURA_DRIVE.get(),
                player.level());
        drive.setPos(position.x, position.y, position.z);
        drive.setOwner(player);
        drive.setColor(color);
        drive.setRotationRoll(roll);
        drive.setBaseSize(profile.mainSize());
        drive.setSpeed(profile.speed());
        drive.setLifetime(profile.lifetimeTicks());
        drive.setDamage(damage);
        drive.setDecoration(decoration);
        drive.shoot(direction.x, direction.y, direction.z, profile.speed(), 0.0F);
        player.level().addFreshEntity(drive);
    }

    private SwordAuraSpawner() {}
}
