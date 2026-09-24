package dev.bladetetra.combat;

import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a player-authored two-motion Slash Art without invoking source SA logic.
 *
 * <p>Primary and secondary each own a visual-only ComboState. The runtime commits
 * the secondary motion at the compiled handoff point, then schedules the secondary
 * attack relative to that second motion. Combat output remains fully bounded by
 * {@link ForgedSlashArtPlan}.</p>
 */
final class ForgedSlashArtHandler {
    private static final double TARGET_RANGE = 18.0D;
    private static final Map<UUID, PendingCast> PENDING = new HashMap<>();

    static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ItemStack blade, ISlashBladeState state) {
        if (!ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                .equals(state.getSlashArtsKey())) {
            return;
        }

        ForgedSlashArtPlan plan = ForgedSlashArtPlan.from(blade);
        if (plan == null) {
            PENDING.remove(player.getUUID());
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 aim = look.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
        Entity locked = state.getTargetEntity(player.level());
        UUID targetId = locked instanceof LivingEntity target
                && validTarget(player, target) ? target.getUUID() : null;

        int created = player.tickCount;
        PENDING.put(player.getUUID(), new PendingCast(
                player.level().dimension(),
                player.getUUID(),
                targetId,
                plan,
                aim,
                event.getComboState(),
                created,
                created + plan.primaryDelayTicks(),
                created + plan.handoffDelayTicks()));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<Map.Entry<UUID, PendingCast>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCast> entry = iterator.next();
            PendingCast pending = entry.getValue();
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);

            if (level == null || player == null || player.level() != level
                    || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            ItemStack blade = player.getMainHandItem();
            ISlashBladeState state = blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .orElse(null);
            ForgedSlashArtPlan current = ForgedSlashArtPlan.from(blade);
            if (state == null || current == null
                    || !pending.plan.key().equals(current.key())
                    || !ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                            .equals(state.getSlashArtsKey())) {
                iterator.remove();
                continue;
            }

            if (!pending.armed) {
                if (player.tickCount <= pending.createdPlayerTick) {
                    continue;
                }
                if (pending.expectedCombo != null
                        && !pending.expectedCombo.equals(state.getComboSeq())) {
                    // Another listener cancelled or redirected the initial motion.
                    iterator.remove();
                    continue;
                }
                pending.armed = true;
            } else if (!isCompatibleMotion(state.getComboSeq(), pending.expectedCombo)) {
                // NONE/STANDBY is normal recovery. Any other committed motion means
                // another cast/input owns the player now, so do not inject our phase.
                iterator.remove();
                continue;
            }

            if (!pending.primaryExecuted
                    && player.tickCount >= pending.primaryDueTick) {
                pending.primaryExecuted = true;
                executePhase(player, state, pending,
                        pending.plan.primary(),
                        pending.plan.primaryCount(),
                        pending.plan.primaryDamagePerHit(),
                        0);
            }

            if (!pending.secondaryStarted
                    && player.tickCount >= pending.handoffDueTick) {
                ResourceLocation secondaryMotion = pending.plan.secondaryMotionId();
                state.updateComboSeq(player, secondaryMotion);
                if (!secondaryMotion.equals(state.getComboSeq())) {
                    iterator.remove();
                    continue;
                }

                pending.secondaryStarted = true;
                pending.expectedCombo = secondaryMotion;
                pending.secondaryDueTick =
                        player.tickCount + pending.plan.secondaryDelayTicks();
                continue;
            }

            if (!pending.secondaryStarted
                    || player.tickCount < pending.secondaryDueTick) {
                continue;
            }

            executePhase(player, state, pending,
                    pending.plan.secondary(),
                    pending.plan.secondaryCount(),
                    pending.plan.secondaryDamagePerHit(),
                    0);
            pending.secondaryCycle++;
            if (pending.secondaryCycle < pending.plan.secondaryCycles()) {
                pending.secondaryDueTick =
                        player.tickCount + pending.plan.echoSpacingTicks();
                continue;
            }
            iterator.remove();
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING.values().removeIf(
                pending -> pending.dimension.equals(level.dimension()));
    }

    static void clear() {
        PENDING.clear();
    }

    private static void executePhase(ServerPlayer player,
            ISlashBladeState state, PendingCast pending,
            ForgedSlashArtPlan.Technique technique,
            int count, double damagePerHit, int delay) {
        LivingEntity target = resolveTarget(player, pending.targetId);
        ProceduralSlashArtExecutor.executeForged(
                player,
                state,
                pending.aim,
                target,
                technique,
                pending.plan.modifier(),
                count,
                damagePerHit,
                delay,
                pending.plan.angleScale());
    }

    private static LivingEntity resolveTarget(
            ServerPlayer player, UUID targetId) {
        if (targetId == null) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(targetId);
        return entity instanceof LivingEntity living
                && validTarget(player, living) ? living : null;
    }

    private static boolean validTarget(
            ServerPlayer player, LivingEntity target) {
        return target.level() == player.level()
                && player.distanceToSqr(target) <= TARGET_RANGE * TARGET_RANGE
                && player.hasLineOfSight(target)
                && LegacyFusionCombatSupport.canAffect(player, target);
    }

    private static boolean isCompatibleMotion(
            ResourceLocation current, ResourceLocation expected) {
        return expected == null
                || expected.equals(current)
                || ComboStateRegistry.NONE.getId().equals(current)
                || ComboStateRegistry.STANDBY.getId().equals(current);
    }

    private static final class PendingCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final UUID targetId;
        private final ForgedSlashArtPlan plan;
        private final Vec3 aim;
        private ResourceLocation expectedCombo;
        private final int createdPlayerTick;
        private final int primaryDueTick;
        private final int handoffDueTick;
        private int secondaryDueTick;
        private boolean armed;
        private boolean primaryExecuted;
        private boolean secondaryStarted;
        private int secondaryCycle;

        private PendingCast(ResourceKey<Level> dimension,
                UUID playerId,
                UUID targetId,
                ForgedSlashArtPlan plan,
                Vec3 aim,
                ResourceLocation expectedCombo,
                int createdPlayerTick,
                int primaryDueTick,
                int handoffDueTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.plan = plan;
            this.aim = aim;
            this.expectedCombo = expectedCombo;
            this.createdPlayerTick = createdPlayerTick;
            this.primaryDueTick = primaryDueTick;
            this.handoffDueTick = handoffDueTick;
        }
    }

    private ForgedSlashArtHandler() {
    }
}
