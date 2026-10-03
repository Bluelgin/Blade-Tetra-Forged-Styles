package dev.bladetetra.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared geometry predicates for style-specific target filtering. */
final class StyleTargeting {
    static boolean isInsideFrontArc(
            LivingEntity attacker,
            LivingEntity target,
            double range,
            double minimumDot) {
        Vec3 offset = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(attacker.position()
                        .add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D));
        Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);
        if (horizontalOffset.lengthSqr() > range * range) {
            return false;
        }
        if (horizontalOffset.lengthSqr() < 1.0E-6D) {
            return true;
        }

        Vec3 look = attacker.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        return horizontalLook.normalize().dot(horizontalOffset.normalize()) >= minimumDot;
    }

    private StyleTargeting() {
    }
}
