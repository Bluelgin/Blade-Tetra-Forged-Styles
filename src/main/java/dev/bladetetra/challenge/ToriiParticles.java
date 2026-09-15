package dev.bladetetra.challenge;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Draws a recognisable, directional torii instead of a rectangular portal. */
final class ToriiParticles {
    private ToriiParticles() {
    }

    static void render(ServerLevel level, Vec3 base, Vec3 right, boolean echo, double scale) {
        // Boundary Forging's full-size graduation gate is reconstructed by the
        // dedicated client renderer. Keep the smaller challenge torii helpers,
        // but do not layer the old Dust outline over the new solid projection.
        if (echo && scale >= 0.88D) {
            return;
        }
        if (level.getGameTime() % 2L != 0L) {
            return;
        }
        right = new Vec3(right.x, 0.0D, right.z).normalize();
        Vec3 depth = new Vec3(-right.z, 0.0D, right.x);
        DustParticleOptions red = new DustParticleOptions(echo
                ? new Vector3f(0.60F, 0.015F, 0.045F)
                : new Vector3f(0.88F, 0.055F, 0.12F), 0.72F);
        DustParticleOptions gold = new DustParticleOptions(
                new Vector3f(0.96F, 0.56F, 0.12F), 0.62F);
        for (double d : new double[] {-0.13D, 0.13D}) {
            Vec3 offset = depth.scale(d * scale);
            // Hashira: the feet open outwards, while the tops draw inward.
            line(level, red, point(base, right, offset, -1.62, 0, scale),
                    point(base, right, offset, -1.30, 3.12, scale), scale);
            line(level, red, point(base, right, offset, 1.62, 0, scale),
                    point(base, right, offset, 1.30, 3.12, scale), scale);
            // Nuki and upper tie beam.
            line(level, red, point(base, right, offset, -2.25, 2.13, scale),
                    point(base, right, offset, 2.25, 2.13, scale), scale);
            line(level, red, point(base, right, offset, -2.73, 2.86, scale),
                    point(base, right, offset, 2.73, 2.86, scale), scale);
            // Curved kasagi, with visibly raised ends.
            line(level, red, point(base, right, offset, -3.25, 3.24, scale),
                    point(base, right, offset, -2.25, 3.10, scale), scale);
            line(level, red, point(base, right, offset, -2.25, 3.10, scale),
                    point(base, right, offset, 2.25, 3.10, scale), scale);
            line(level, red, point(base, right, offset, 2.25, 3.10, scale),
                    point(base, right, offset, 3.25, 3.24, scale), scale);
        }
        // Small central plaque gives the silhouette a shrine-like focal point.
        Vec3 plaque = base.add(0.0D, 2.47D * scale, 0.0D);
        line(level, gold, plaque.add(right.scale(-0.25D * scale)),
                plaque.add(right.scale(0.25D * scale)), scale);
        line(level, gold, plaque.add(right.scale(-0.25D * scale)),
                plaque.add(right.scale(-0.25D * scale)).add(0, 0.35D * scale, 0), scale);
        line(level, gold, plaque.add(right.scale(0.25D * scale)),
                plaque.add(right.scale(0.25D * scale)).add(0, 0.35D * scale, 0), scale);
    }

    static void renderSweep(ServerLevel level, Vec3 center, Vec3 direction,
            boolean warning) {
        if (level.getGameTime() % 2L != 0L) {
            return;
        }
        Vec3 forward = new Vec3(direction.x, 0.0D, direction.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int phase = (int) (level.getGameTime() / 2L % 4L);
        for (int i = phase; i < 12; i += 4) {
            render(level, center.add(forward.scale(4.0D + i * 4.4D)),
                    right, true, warning ? 0.58D : 0.76D);
        }
        DustParticleOptions line = new DustParticleOptions(
                warning ? new Vector3f(0.55F, 0.025F, 0.055F)
                        : new Vector3f(0.95F, 0.025F, 0.08F),
                warning ? 0.62F : 0.9F);
        for (int i = 2; i <= 58; i += 2) {
            Vec3 at = center.add(forward.scale(i));
            level.sendParticles(line, at.x, center.y + 0.12D, at.z,
                    1, 0.08D, 0.02D, 0.08D, 0.0D);
        }
    }

    static void renderCage(ServerLevel level, Vec3 center, double radius,
            boolean active) {
        if (level.getGameTime() % 2L != 0L) {
            return;
        }
        int gate = (int) (level.getGameTime() / 2L % 4L);
        double angle = Math.PI * 0.5D * gate;
        Vec3 outward = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 tangent = new Vec3(-outward.z, 0.0D, outward.x);
        render(level, center.add(outward.scale(radius)), tangent, true,
                active ? 0.82D : 0.64D);

        DustParticleOptions ring = new DustParticleOptions(
                active ? new Vector3f(0.90F, 0.025F, 0.09F)
                        : new Vector3f(0.48F, 0.02F, 0.06F),
                active ? 0.82F : 0.58F);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2.0D * i / 40.0D;
            level.sendParticles(ring,
                    center.x + Math.cos(a) * radius,
                    center.y + 0.10D,
                    center.z + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private static Vec3 point(Vec3 base, Vec3 right, Vec3 depth,
            double x, double y, double scale) {
        return base.add(right.scale(x * scale)).add(depth).add(0.0D, y * scale, 0.0D);
    }

    private static void line(ServerLevel level, DustParticleOptions dust,
            Vec3 from, Vec3 to, double scale) {
        double length = from.distanceTo(to);
        int points = Math.max(2, (int) Math.ceil(length / (0.18D * scale)));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.lerp(to, i / (double) points);
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
