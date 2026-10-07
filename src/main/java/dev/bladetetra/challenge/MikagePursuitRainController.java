package dev.bladetetra.challenge;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

import static dev.bladetetra.challenge.MikageDefenseController.PursuitPressure;

/**
 * Owns Mikage's anti-pressure Pursuit Rain sequence.
 *
 * <p>Mutable encounter state remains in {@link MikageTechniqueRuntime} and
 * {@link MikageDefenseController}; this controller owns only the behavior that
 * reads and advances that state.</p>
 */
final class MikagePursuitRainController {
    private final MikageEntity owner;

    MikagePursuitRainController(MikageEntity owner) {
        this.owner = owner;
    }

    void registerPressure(ServerPlayer attacker, long now) {
        MikageDefenseController defense = owner.defenseController();
        PursuitPressure pressure = defense.pursuitPressure.computeIfAbsent(
                attacker.getUUID(), id -> new PursuitPressure());
        if (pressure.lastHit == Long.MIN_VALUE || now - pressure.lastHit > 80L) {
            pressure.hits = 0;
        }
        pressure.lastHit = now;
        if (pressure.lastCountedHit == Long.MIN_VALUE
                || now - pressure.lastCountedHit >= 8L) {
            pressure.hits++;
            pressure.lastCountedHit = now;
        }
    }

    ServerPlayer selectTarget(ServerLevel server) {
        MikageDefenseController defense = owner.defenseController();
        int threshold = GameplayConfig.MIKAGE_PURSUIT_RAIN_HIT_THRESHOLD.get();
        long now = owner.level().getGameTime();
        ServerPlayer selected = null;
        int highest = threshold - 1;
        for (Map.Entry<UUID, PursuitPressure> entry : defense.pursuitPressure.entrySet()) {
            PursuitPressure pressure = entry.getValue();
            if (pressure.hits < threshold || now - pressure.lastHit > 120L) {
                continue;
            }
            Player found = server.getPlayerByUUID(entry.getKey());
            if (!(found instanceof ServerPlayer player) || !player.isAlive()
                    || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) {
                continue;
            }
            if (player.getHealth() <= player.getMaxHealth() * 0.30F) {
                continue;
            }
            if (pressure.hits > highest) {
                highest = pressure.hits;
                selected = player;
            }
        }
        return selected;
    }

    void begin(ServerPlayer target, ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        MikageDefenseController defense = owner.defenseController();
        techniques.pursuitRainTarget = target.getUUID();
        techniques.pursuitRainFinalTicks = 0;
        techniques.pursuitRainFinalSword = null;
        techniques.pursuitRainFinalLaunched = false;
        techniques.pursuitRainCountered = false;
        techniques.pursuitRainActiveTicks =
                GameplayConfig.MIKAGE_PURSUIT_RAIN_DURATION_TICKS.get();
        techniques.pursuitRainTicks = techniques.pursuitRainActiveTicks
                + MikageLegacyTiming.PURSUIT_RAIN_WARNING_TICKS;
        techniques.pursuitRainWave = 0;
        PursuitPressure pressure = defense.pursuitPressure.get(target.getUUID());
        if (pressure != null) {
            pressure.hits = 0;
        }
        owner.setAction(
                MikageEntity.MikageAction.CAST_READY,
                MikageLegacyTiming.PURSUIT_RAIN_WARNING_TICKS);
        server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_RETURN,
                SoundSource.HOSTILE, 1.1F, 0.65F);
        server.sendParticles(new DustParticleOptions(
                        new Vector3f(0.86F, 0.025F, 0.09F), 1.25F),
                target.getX(), target.getY() + 3.1D, target.getZ(),
                28, 0.55D, 0.20D, 0.55D, 0.02D);
        Vec3 lock = target.position().add(0.0D, 2.8D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.PURSUIT_LOCK,
                lock.x, lock.y, lock.z, lock.x, lock.y, lock.z,
                target.getYRot(), 1.0F, -1, target.getId(),
                techniques.pursuitRainTicks, owner.getRandom().nextInt()), lock);
    }

    void tick(ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        Player found = techniques.pursuitRainTarget == null
                ? null : server.getPlayerByUUID(techniques.pursuitRainTarget);
        ServerPlayer target = found instanceof ServerPlayer player ? player : null;
        if (target == null || !target.isAlive() || target.isCreative()
                || target.isSpectator()
                || !ChallengeManager.isParticipant(owner, target)) {
            finish(server);
            return;
        }

        boolean warning =
                techniques.pursuitRainTicks > techniques.pursuitRainActiveTicks;
        if (warning) {
            if (techniques.pursuitRainTicks % 4 == 0) {
                server.sendParticles(new DustParticleOptions(
                                new Vector3f(0.92F, 0.02F, 0.08F), 0.9F),
                        target.getX(), target.getY() + 3.0D, target.getZ(),
                        8, 0.35D, 0.08D, 0.35D, 0.0D);
            }
        } else {
            if (techniques.pursuitRainTicks == techniques.pursuitRainActiveTicks) {
                owner.setAction(MikageEntity.MikageAction.IDLE, 1);
            }
            int interval = GameplayConfig.MIKAGE_PURSUIT_RAIN_INTERVAL_TICKS.get();
            if ((techniques.pursuitRainActiveTicks - techniques.pursuitRainTicks)
                    % interval == 0) {
                castWave(target, server);
            }
        }

        if (--techniques.pursuitRainTicks <= 0) {
            beginFinal(target, server);
        }
    }

    void tickFinal(ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        Player found = techniques.pursuitRainTarget == null
                ? null : server.getPlayerByUUID(techniques.pursuitRainTarget);
        ServerPlayer target = found instanceof ServerPlayer player ? player : null;
        Entity entity = techniques.pursuitRainFinalSword == null
                ? null : server.getEntity(techniques.pursuitRainFinalSword);
        EntityAbstractSummonedSword sword =
                entity instanceof EntityAbstractSummonedSword summoned
                        ? summoned : null;
        if (target == null || !target.isAlive() || target.isCreative()
                || target.isSpectator()
                || !ChallengeManager.isParticipant(owner, target)) {
            finish(server);
            return;
        }

        if (!techniques.pursuitRainFinalLaunched) {
            if (sword != null) {
                sword.setPos(techniques.pursuitRainFinalOrigin);
                sword.setDeltaMovement(Vec3.ZERO);
            }
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(1.0F, 0.025F, 0.08F), 1.15F),
                    techniques.pursuitRainFinalOrigin.x,
                    techniques.pursuitRainFinalOrigin.y,
                    techniques.pursuitRainFinalOrigin.z,
                    7, 0.32D, 0.32D, 0.32D, 0.01D);
        }

        if (!techniques.pursuitRainFinalLaunched
                && techniques.pursuitRainFinalTicks
                == MikageLegacyTiming.PURSUIT_RAIN_FINAL_LAUNCH_TICK) {
            if (isFinalGuarding(target)) {
                reflectFinalSword(target, sword, server);
                return;
            }
            techniques.pursuitRainFinalLaunched = true;
            Vec3 direction = techniques.pursuitRainFinalAim.subtract(
                    sword == null
                            ? techniques.pursuitRainFinalOrigin
                            : sword.position());
            if (sword != null) {
                sword.setNoClip(false);
                sword.shoot(direction.x, direction.y, direction.z, 1.85F, 0.0F);
            }
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * GameplayConfig.MIKAGE_PURSUIT_RAIN_FINAL_DAMAGE_MULTIPLIER
                    .get().floatValue();
            int travelTicks = Math.max(2, Mth.ceil(direction.length() / 1.85D));
            owner.attackTimeline().circle(
                    travelTicks,
                    techniques.pursuitRainFinalAim,
                    1.30D, 2.8D, damage, 0.20D);
            server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THROW,
                    SoundSource.HOSTILE, 0.9F, 0.75F);
        }

        if (--techniques.pursuitRainFinalTicks <= 0) {
            finish(server);
        }
    }

    private void castWave(ServerPlayer target, ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        Vec3 velocity = target.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        Vec3 travel = velocity.lengthSqr() > 0.0025D
                ? velocity.normalize()
                : target.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        if (travel.lengthSqr() < 0.01D) {
            travel = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 rear = travel.reverse();
        Vec3 right = new Vec3(-travel.z, 0.0D, travel.x);
        int lane = techniques.pursuitRainWave++ % 3;
        Vec3 originOffset = switch (lane) {
            case 0 -> rear.scale(4.8D).add(right.scale(-2.8D))
                    .add(0.0D, 3.4D, 0.0D);
            case 1 -> rear.scale(4.8D).add(right.scale(2.8D))
                    .add(0.0D, 3.4D, 0.0D);
            default -> rear.scale(1.4D).add(0.0D, 6.2D, 0.0D);
        };
        Vec3 origin = target.getEyePosition().add(originOffset);
        Vec3 lockedAim = target.getEyePosition();
        Vec3 direction = lockedAim.subtract(origin);
        double distance = direction.length();

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, server);
        sword.setOwner(owner);
        sword.setShooter(owner);
        sword.setColor(0xD51F3F);
        sword.setDamage(0.0D);
        sword.setRoll(lane == 0 ? -18.0F : lane == 1 ? 18.0F : 0.0F);
        sword.setPos(origin);
        Vec3 shot = direction.normalize();
        float speed = 1.65F;
        sword.shoot(shot.x, shot.y, shot.z, speed, 0.0F);
        sword.getPersistentData().putBoolean("blade_tetra_mikage_attack", true);
        server.addFreshEntity(sword);

        float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * GameplayConfig.MIKAGE_PURSUIT_RAIN_DAMAGE_MULTIPLIER
                .get().floatValue();
        int travelTicks = Math.max(3, Mth.ceil(distance / speed));
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.PURSUIT_SWORD,
                origin.x, origin.y, origin.z,
                lockedAim.x, lockedAim.y, lockedAim.z,
                0.0F, 0.88F, sword.getId(), -1,
                travelTicks + 7, lane), lockedAim);
        owner.attackTimeline().circle(
                travelTicks, target.position(), 1.25D, 2.6D, damage, 0.12D);
        if (techniques.pursuitRainWave % 3 == 1) {
            server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THROW,
                    SoundSource.HOSTILE, 0.38F, 1.65F);
        }
    }

    private void beginFinal(ServerPlayer target, ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        techniques.pursuitRainTicks = 0;
        techniques.pursuitRainFinalTicks =
                MikageLegacyTiming.PURSUIT_RAIN_FINAL_TICKS;
        techniques.pursuitRainFinalLaunched = false;
        Vec3 facing = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (facing.lengthSqr() < 0.01D) {
            facing = target.position().subtract(owner.position())
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (facing.lengthSqr() < 0.01D) {
            facing = new Vec3(0.0D, 0.0D, 1.0D);
        }
        facing = facing.normalize();
        techniques.pursuitRainFinalOrigin =
                target.getEyePosition().add(facing.scale(5.2D))
                        .add(0.0D, 0.8D, 0.0D);
        techniques.pursuitRainFinalAim = target.getEyePosition();

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, server);
        sword.setOwner(owner);
        sword.setShooter(owner);
        sword.setColor(0xFF1838);
        sword.setDamage(0.0D);
        sword.setNoClip(true);
        sword.setPos(techniques.pursuitRainFinalOrigin);
        sword.setDeltaMovement(Vec3.ZERO);
        Vec3 aim = techniques.pursuitRainFinalAim
                .subtract(techniques.pursuitRainFinalOrigin).normalize();
        sword.setYRot((float) (Mth.atan2(aim.x, aim.z) * Mth.RAD_TO_DEG));
        sword.setXRot((float) (Mth.atan2(
                aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))
                * Mth.RAD_TO_DEG));
        sword.getPersistentData().putBoolean("blade_tetra_mikage_attack", true);
        sword.getPersistentData().putBoolean("blade_tetra_pursuit_final", true);
        server.addFreshEntity(sword);
        techniques.pursuitRainFinalSword = sword.getUUID();
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.PURSUIT_RETURN,
                techniques.pursuitRainFinalOrigin.x,
                techniques.pursuitRainFinalOrigin.y,
                techniques.pursuitRainFinalOrigin.z,
                techniques.pursuitRainFinalAim.x,
                techniques.pursuitRainFinalAim.y,
                techniques.pursuitRainFinalAim.z,
                owner.getYRot(), 1.0F, sword.getId(), -1,
                MikageLegacyTiming.PURSUIT_RAIN_FINAL_TICKS,
                owner.getRandom().nextInt()),
                techniques.pursuitRainFinalAim);
        server.playSound(null, target.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 1.0F, 1.7F);
    }

    private boolean isFinalGuarding(ServerPlayer player) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        if (!owner.legacyEffects().isBladeGuarding(player)) {
            return false;
        }
        Vec3 toSword = techniques.pursuitRainFinalOrigin
                .subtract(player.getEyePosition());
        return toSword.lengthSqr() > 0.01D
                && player.getLookAngle().normalize()
                .dot(toSword.normalize()) >= 0.35D;
    }

    private void reflectFinalSword(
            ServerPlayer player,
            EntityAbstractSummonedSword sword,
            ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        MikageCombatDirector combat = owner.combatDirector();
        owner.attackTimeline().clear();
        Vec3 clashStart = player.getEyePosition();
        Vec3 clashEnd = owner.getEyePosition();
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.COUNTER_CLASH,
                clashStart.x, clashStart.y, clashStart.z,
                clashEnd.x, clashEnd.y, clashEnd.z,
                player.getYRot(), 1.12F, -1, -1, 14,
                owner.getRandom().nextInt()), clashStart);
        if (sword != null) {
            Vec3 origin = player.getEyePosition()
                    .add(player.getLookAngle().normalize().scale(1.1D));
            Vec3 reflected = owner.getEyePosition().subtract(origin).normalize();
            sword.setPos(origin);
            sword.setNoClip(true);
            sword.setColor(0xFFB0B8);
            sword.shoot(reflected.x, reflected.y, reflected.z, 2.2F, 0.0F);
        }
        if (owner.isSwordWheelDeployed()) {
            owner.swordWheel().recallSwordWheel(server, 90);
        }
        techniques.pursuitRainTicks = 0;
        techniques.pursuitRainFinalTicks = 0;
        techniques.pursuitRainFinalLaunched = true;
        techniques.pursuitRainTarget = null;
        techniques.pursuitRainFinalSword = null;
        techniques.pursuitRainCountered = true;
        combat.signatureRecoveryTicks =
                GameplayConfig.MIKAGE_PURSUIT_RAIN_STAGGER_TICKS.get();
        techniques.pursuitRainCooldown = combat.scaledCooldown(
                GameplayConfig.MIKAGE_PURSUIT_RAIN_COOLDOWN_TICKS.get());
        combat.techniqueCooldown =
                Math.max(combat.techniqueCooldown, combat.signatureRecoveryTicks);
        owner.setAction(
                MikageEntity.MikageAction.STAGGERED,
                combat.signatureRecoveryTicks);
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        server.playSound(null, owner.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.15F, 1.75F);
        server.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                owner.getX(), owner.getY() + 1.0D, owner.getZ(),
                45, 0.8D, 1.0D, 0.8D, 0.12D);
        for (ServerPlayer participant : server.players()) {
            if (ChallengeManager.isParticipant(owner, participant)) {
                participant.displayClientMessage(Component.translatable(
                        "message.blade_tetra.mikage.pursuit_stagger"), true);
            }
        }
    }

    private void finish(ServerLevel server) {
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        MikageCombatDirector combat = owner.combatDirector();
        if (!techniques.pursuitRainFinalLaunched
                && techniques.pursuitRainFinalSword != null) {
            Entity sword = server.getEntity(techniques.pursuitRainFinalSword);
            if (sword != null) {
                sword.discard();
            }
        }
        techniques.pursuitRainTicks = 0;
        techniques.pursuitRainFinalTicks = 0;
        techniques.pursuitRainTarget = null;
        techniques.pursuitRainFinalSword = null;
        techniques.pursuitRainFinalLaunched = false;
        techniques.pursuitRainCooldown = combat.scaledCooldown(
                GameplayConfig.MIKAGE_PURSUIT_RAIN_COOLDOWN_TICKS.get());
        combat.techniqueCooldown = Math.max(
                combat.techniqueCooldown, combat.scaledCooldown(40));
        owner.setAction(MikageEntity.MikageAction.IDLE, 1);
    }
}
