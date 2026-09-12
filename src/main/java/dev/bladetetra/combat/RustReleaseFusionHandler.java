package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.common.ForgeMod;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/** Normal-combo SE for the sealed Agito saya and awakened Orotiagito hilt. */
final class RustReleaseFusionHandler {
    private static final double EXTRA_EDGE_RANGE = 1.5D;
    private static final double EDGE_RADIUS = 0.95D;
    private static final Map<ServerPlayer, Long> LAST_FINISHER_TICK =
            new WeakHashMap<>();
    private static final ThreadLocal<Boolean> EDGE_DAMAGE =
            ThreadLocal.withInitial(() -> false);

    static void onLivingHurt(LivingHurtEvent event) {
        if (EDGE_DAMAGE.get()) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player) return;
        ItemStack blade = player.getMainHandItem();
        if (LegacyFusion.active(blade)
                != LegacyFusion.SEALED_AGITO_SAYA_OROTIAGITO_HILT) return;
        int phase = blade.getCapability(
                        mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                .map(state -> phase(state.getComboSeq())).orElse(0);
        if (phase == 0) return;

        LivingEntity target = event.getEntity();
        double armor = event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                ? 0.0D : target.getArmorValue();
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        event.setAmount(RustReleaseBalance.adjustedIncomingDamage(
                event.getAmount(), armor, toughness, phase));
    }

    static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !LegacyFusionCombatSupport.canAffect(player, event.getTarget())) return;
        ItemStack blade = player.getMainHandItem();
        if (LegacyFusion.active(blade)
                != LegacyFusion.SEALED_AGITO_SAYA_OROTIAGITO_HILT) return;
        int phase = phase(event.getSlashBladeState().getComboSeq());
        if (phase == 0) return;

        releaseParticles(level, event.getTarget(), phase);
        level.playSound(null, event.getTarget().blockPosition(),
                phase == RustReleaseBalance.FINISHER
                        ? SoundEvents.TRIDENT_RETURN : SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS,
                phase == RustReleaseBalance.FINISHER ? 0.72F : 0.16F,
                0.78F + phase * 0.10F);
        if (phase == RustReleaseBalance.FINISHER) {
            releaseFinisherEdge(level, player, event.getTarget());
        }
    }

    static int phase(ResourceLocation combo) {
        if (combo == null) return 0;
        if (same(combo, ComboStateRegistry.COMBO_A4.getId())
                || same(combo, ComboStateRegistry.COMBO_A5.getId())
                || same(combo, ComboStateRegistry.COMBO_B7.getId())
                || same(combo, ComboStateRegistry.AERIAL_RAVE_A3.getId())
                || same(combo, ComboStateRegistry.AERIAL_RAVE_B4.getId())
                || ModComboStates.isIaidoFinish(combo)
                || ModComboStates.isDangakuCleave(combo)) {
            return RustReleaseBalance.FINISHER;
        }
        if (same(combo, ComboStateRegistry.COMBO_A3.getId())
                || same(combo, ComboStateRegistry.COMBO_B4.getId())
                || same(combo, ComboStateRegistry.COMBO_B5.getId())
                || same(combo, ComboStateRegistry.COMBO_B6.getId())
                || same(combo, ComboStateRegistry.AERIAL_RAVE_A2.getId())
                || same(combo, ComboStateRegistry.AERIAL_RAVE_B3.getId())
                || same(combo, ModComboStates.IAIDO_RETURN.getId())
                || ModComboStates.isDangakuRise(combo)) {
            return RustReleaseBalance.LATE;
        }
        if (same(combo, ComboStateRegistry.COMBO_A2.getId())
                || same(combo, ComboStateRegistry.COMBO_B2.getId())
                || same(combo, ComboStateRegistry.COMBO_B3.getId())
                || same(combo, ModComboStates.IAIDO_FOLLOW.getId())) {
            return RustReleaseBalance.MIDDLE;
        }
        if (same(combo, ComboStateRegistry.COMBO_A1.getId())
                || same(combo, ComboStateRegistry.COMBO_B1.getId())
                || same(combo, ComboStateRegistry.COMBO_C.getId())
                || same(combo, ComboStateRegistry.AERIAL_RAVE_A1.getId())
                || ModComboStates.isIaidoDraw(combo)
                || ModComboStates.isDangakuSweep(combo)) {
            return RustReleaseBalance.OPENING;
        }
        return 0;
    }

    private static void releaseFinisherEdge(ServerLevel level,
            ServerPlayer player, LivingEntity originalTarget) {
        long now = level.getGameTime();
        if (LAST_FINISHER_TICK.getOrDefault(player, Long.MIN_VALUE) == now) return;
        LAST_FINISHER_TICK.put(player, now);

        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition();
        double normalReach = Math.max(2.5D,
                player.getAttributeValue(ForgeMod.ENTITY_REACH.get()));
        Vec3 intendedEnd = start.add(direction.scale(normalReach + EXTRA_EDGE_RANGE));
        var blockHit = level.clip(new ClipContext(start, intendedEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS
                ? intendedEnd : blockHit.getLocation();
        LivingEntity edgeTarget = findEdgeTarget(
                level, player, originalTarget, start, end, normalReach);
        Vec3 impact = edgeTarget == null ? end : edgeTarget.getBoundingBox().getCenter();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        LegacyFusionCombatSupport.spawnVisualSlash(player, impact,
                yaw + 90.0F, 8.0F, 0x75E5A2, 1.18F, 8);
        level.sendParticles(ParticleTypes.END_ROD,
                impact.x, impact.y, impact.z, 8,
                0.24D, 0.28D, 0.24D, 0.035D);
        if (edgeTarget != null) {
            EDGE_DAMAGE.set(true);
            try {
                LegacyFusionCombatSupport.hurtPreservingIFrames(
                        level, player, edgeTarget,
                        RustReleaseBalance.edgeDamage(player.getAttributeValue(
                                Attributes.ATTACK_DAMAGE)));
            } finally {
                EDGE_DAMAGE.remove();
            }
        }
    }

    private static LivingEntity findEdgeTarget(ServerLevel level,
            ServerPlayer player, LivingEntity originalTarget, Vec3 start, Vec3 end,
            double normalReach) {
        Vec3 path = end.subtract(start);
        double lengthSquared = path.lengthSqr();
        if (lengthSquared < 1.0E-6D) return null;
        return level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(start, end).inflate(EDGE_RADIUS),
                        target -> target != originalTarget
                                && LegacyFusionCombatSupport.canAffect(player, target))
                .stream()
                .filter(target -> target.getBoundingBox().getCenter()
                        .subtract(start).dot(path.normalize()) >= normalReach - 0.25D)
                .filter(target -> distanceToSegmentSquared(
                        target.getBoundingBox().getCenter(), start, end)
                        <= Math.pow(EDGE_RADIUS + target.getBbWidth() * 0.3D, 2.0D))
                .min(Comparator.comparingDouble(target ->
                        target.getBoundingBox().getCenter().subtract(start)
                                .dot(path) / lengthSquared))
                .orElse(null);
    }

    private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        double progress = Math.max(0.0D, Math.min(1.0D,
                point.subtract(start).dot(segment) / lengthSquared));
        return point.distanceToSqr(start.add(segment.scale(progress)));
    }

    private static void releaseParticles(ServerLevel level,
            LivingEntity target, int phase) {
        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ASH,
                center.x, center.y, center.z, 3 + phase * 2,
                target.getBbWidth() * 0.28D, target.getBbHeight() * 0.24D,
                target.getBbWidth() * 0.28D, 0.018D);
        if (phase >= RustReleaseBalance.MIDDLE) {
            float green = 0.22F + phase * 0.11F;
            level.sendParticles(new DustParticleOptions(
                            new Vector3f(0.18F, green, 0.24F), 0.82F + phase * 0.08F),
                    center.x, center.y, center.z, 3 + phase,
                    target.getBbWidth() * 0.22D, target.getBbHeight() * 0.20D,
                    target.getBbWidth() * 0.22D, 0.012D);
        }
        if (phase >= RustReleaseBalance.LATE) {
            level.sendParticles(ParticleTypes.CRIT,
                    center.x, center.y, center.z, phase == RustReleaseBalance.FINISHER ? 9 : 4,
                    0.24D, 0.28D, 0.24D, 0.045D);
        }
    }

    static void clear() {
        LAST_FINISHER_TICK.clear();
        EDGE_DAMAGE.remove();
    }

    private static boolean same(ResourceLocation first, ResourceLocation second) {
        return first.equals(second);
    }

    private RustReleaseFusionHandler() {
    }
}
