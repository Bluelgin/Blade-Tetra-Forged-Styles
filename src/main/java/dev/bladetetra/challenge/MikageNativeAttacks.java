package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import dev.bladetetra.registry.ModEntities;
import mods.flammpfeil.slashblade.slasharts.*;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.*;
import org.joml.Vector3f;

/** Native attack creation. No timeline, circles or lines that manufacture damage after a VFX. */
final class MikageNativeAttacks {
    static void release(MikageEntity owner, CastScope scope, MikageMove move, Vec3 aim, Vec3 heading, int age) {
        switch (move) {
            case COMBO -> AttackManager.doSlash(owner, age == 0 ? 25 : age == 14 ? -155 : 205, false, false, .52);
            case IAIDO, DUEL -> AttackManager.doSlash(owner, age == 0 ? -12 : 35, false, false, age == 0 ? .88 : .60);
            case CIRCLE -> { var slash = AttackManager.doSlash(owner, 180, false, false, .68); slash.setIndirect(true); }
            case ZANSHIN -> AttackManager.doSlash(owner, 180, false, false, .68);
            case CLEAVE -> AttackManager.doSlash(owner, age == 0 ? 25 : age == 12 ? -155 : 92, false, age == 30, age == 30 ? 1.08 : .45);
            case SAKURA -> {
                if (age < 30) AttackManager.doSlash(owner, age == 0 ? 30 : -150, false, false, .56);
                else SakuraEnd.doSlash(owner, 30, Vec3.ZERO, false, true, .68);
            }
            case FAN -> { for (float yaw : new float[]{-18, 0, 18}) Drive.doSlash(owner, 0, (float) Math.toRadians(yaw), 30, 0xFF1838, Vec3.ZERO, false, .62, KnockBacks.cancel, 1.65F); }
            case WAVE -> WaveEdge.doSlash(owner, 0, 32, Vec3.ZERO, false, .64, 1.15F, 1.85F, 4);
            case CUT, SUPER_CUT -> {
                Vec3 at = aim;
                if (move == MikageMove.SUPER_CUT) at = aim.add(new Vec3(-heading.z, 0, heading.x).scale((age / 12 - 1) * 4));
                var cut = JudgementCut.doJudgementCut(owner); cut.setPos(at.x, at.y, at.z); cut.setDamage(3.5);
            }
            case VOLLEY -> {
                for (int i = -1; i <= 1; i++) sword(owner, scope,
                        owner.getEyePosition().add(-heading.z * i, .3, heading.x * i), aim, 1.8F, .22, false);
            }
            case RAIN -> {
                for (int i = -1; i <= 1; i++) {
                    Vec3 at = aim.add(-heading.z * i * 2.5, 0, heading.x * i * 2.5).add(heading.scale((age / 12 - 1) * 3));
                    sword(owner, scope, at.add(0, 7, 0), at, .85F, .32, false);
                }
            }
            case CHASE_RAIN -> {
                boolean returning = age == 44;
                Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(age == 14 ? -3 : 3);
                Vec3 origin = returning ? aim.subtract(heading.scale(5)).add(0, 1, 0) : aim.add(side).add(0, 5, 0);
                sword(owner, scope, origin, aim, returning ? .8F : 1.05F, returning ? .5 : .25, returning);
            }
        }
    }
    private static void sword(MikageEntity owner, CastScope scope, Vec3 origin, Vec3 aim,
            float speed, double ratio, boolean returning) {
        ServerLevel level = (ServerLevel) owner.level();
        var sword = new MikageGateSwordEntity(ModEntities.MIKAGE_GATE_SWORD.get(), level);
        sword.configureAttack(owner, scope, returning); sword.setPos(origin);
        sword.setDamage(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * ratio);
        Vec3 direction = aim.subtract(origin).normalize(); sword.shoot(direction.x, direction.y, direction.z, speed, 0);
        level.addFreshEntity(sword);
    }
    static void tell(MikageEntity owner, Vec3 aim, MikageMove move) {
        if (owner.tickCount % 3 != 0) return;
        ServerLevel level = (ServerLevel) owner.level();
        var particle = new DustParticleOptions(new Vector3f(.95F, .025F, .1F), 1);
        if (move == MikageMove.RAIN) {
            Vec3 forward = owner.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            for (int row = -1; row <= 1; row++) for (int column = -1; column <= 1; column++) {
                Vec3 at = aim.add(forward.scale(row * 3)).add(side.scale(column * 2.5));
                level.sendParticles(particle, at.x, at.y - .7, at.z, 5, .4, .05, .4, 0);
            }
        } else if (move == MikageMove.CUT || move == MikageMove.SUPER_CUT || move == MikageMove.CHASE_RAIN) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                level.sendParticles(particle, aim.x + Math.cos(a) * 2, aim.y - .7, aim.z + Math.sin(a) * 2, 1, 0, 0, 0, 0);
            }
            if (move == MikageMove.SUPER_CUT) {
                Vec3 forward = owner.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 side = new Vec3(-forward.z, 0, forward.x);
                for (int sign : new int[]{-1, 1}) level.sendParticles(particle,
                        aim.x + side.x * sign * 4, aim.y, aim.z + side.z * sign * 4, 6, .8, .1, .8, 0);
            }
        } else level.sendParticles(particle, owner.getX(), owner.getY() + 1.4, owner.getZ(), 5, .3, .2, .3, 0);
    }
    private MikageNativeAttacks() { }
}
