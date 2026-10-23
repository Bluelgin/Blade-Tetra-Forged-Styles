package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import dev.bladetetra.registry.ModEntities;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import java.util.*;

/** Two harmless false bodies. The real sheath emits a distinct sound and gold sparks. */
final class MikageMoonEchoExecution implements SkillExecution {
    private final MikageEntity owner;
    private final UUID target;
    private final Runnable committed;
    private final Set<MikageEchoEntity> decoys = new HashSet<>();
    private CastScope scope;
    private int age;
    private boolean interrupted;
    MikageMoonEchoExecution(MikageEntity owner, ServerPlayer player, Runnable committed) {
        this.owner = owner; target = player.getUUID(); this.committed = committed;
    }
    @Override public void start(CastScope scope) {
        this.scope = scope; owner.setMoonEchoActive(true); scope.own(() -> owner.setMoonEchoActive(false)); scope.own(() -> { decoys.forEach(Entity::discard); decoys.clear(); });
        ServerLevel level = (ServerLevel) owner.level();
        for (int sign : new int[]{-1, 1}) {
            Vec3 at = owner.position().add(owner.getLookAngle().yRot(sign * 1.05F).multiply(1, 0, 1).normalize().scale(3));
            if (!MikageFootworkRoutes.clear(owner, at)) continue;
            var echo = new MikageEchoEntity(ModEntities.MIKAGE_ECHO.get(), level);
            echo.configure(owner); echo.setPos(at); if (level.addFreshEntity(echo)) decoys.add(echo);
        }
        owner.setAction(MikageEntity.MikageAction.IAIDO_READY, 36); committed.run();
    }
    @Override public boolean windingUp() { return age < 36; }
    int remaining() { return Math.max(0, 66 - age); }
    boolean owns(MikageEchoEntity echo) { return decoys.contains(echo) && !scope.closed(); }
    void falseStruck(MikageEchoEntity echo, Entity attacker) {
        if (!owns(echo) || !(attacker instanceof ServerPlayer player) || !owner.encounter().eligible(player)) return;
        decoys.remove(echo); echo.discard();
        ((ServerLevel) owner.level()).sendParticles(ParticleTypes.REVERSE_PORTAL, echo.getX(), echo.getY() + 1,
                echo.getZ(), 16, .3, .7, .3, .01);
    }
    boolean struck(ServerPlayer player, DamageSource source) {
        Entity direct = MikageBladeAttackTrace.projectile(); if (direct == null) direct = source.getDirectEntity();
        if (age >= 36 || !owner.encounter().eligible(player) || owner.distanceToSqr(player) > 36
                || direct != player && (!(direct instanceof EntitySlashEffect slash)
                || direct.getClass() != EntitySlashEffect.class || slash.getShooter() != player)) return false;
        var blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ItemSlashBlade) && !blade.canPerformAction(ToolActions.SWORD_SWEEP)) return false;
        interrupted = true; owner.duel().rewardOpening(28); return true;
    }
    @Override public Status tick() {
        if (!(((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer player)
                || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO); ++age;
        if (age <= 28) owner.lookAt(player, 180, 180);
        if (age < 20 && owner.distanceToSqr(player) > 3.4 * 3.4) {
            Vec3 next = owner.position().add(player.position().subtract(owner.position()).multiply(1, 0, 1).normalize().scale(.25));
            if (MikageFootworkRoutes.clear(owner, next)) owner.setPos(next);
        }
        if (age <= 36) {
            var level = (ServerLevel) owner.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1,
                    owner.getZ(), 3, .2, .1, .2, 0);
            if (age == 12 || age == 28) level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.HOSTILE, 1, 1.45F);
        }
        if (age == 36) {
            owner.setAction(MikageEntity.MikageAction.IAIDO_DRAW, 8);
            MikageNativeCombat.run(owner, scope, new MikageNativeCombat.Rule(target, true, false,
                    (p, source) -> {
                        if (!owner.duel().parry(p, owner.getEyePosition())) return true;
                        interrupted = true; owner.duel().rewardOpening(28); return false;
                    }, (p, source) -> { }), () -> AttackManager.doSlash(owner, -12, false, false, .85));
        }
        return interrupted ? Status.COUNTERED : age >= 66 ? Status.COMPLETE : Status.RUNNING;
    }
    @Override public void stop(StopReason reason) {
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.combatDirector().techniqueCooldown = Math.max(24, owner.combatDirector().techniqueCooldown);
    }
}
