package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.entity.IShootable;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a player-authored two-stage Slash Art through SlashBlade's real
 * ComboState graphs while Blade Tetra remains authoritative over combat power.
 *
 * <p>The source graph owns motion, movement, sounds, pose changes, timeout
 * transitions and recovery. Native damage is suppressed while the graph runs;
 * the bounded forged budget is emitted separately at the authored signature
 * timing. Handoff occurs only after SlashBlade naturally leaves A's graph.</p>
 */
final class ForgedSlashArtHandler {
    private static final double TARGET_RANGE = 18.0D;
    private static final Map<UUID, PendingCast> PENDING = new HashMap<>();
    private static final List<PendingDiscard> PENDING_DISCARDS = new ArrayList<>();
    private static final ThreadLocal<Integer> FORGED_OUTPUT_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    static void onSlashArt(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ItemStack blade, ISlashBladeState state) {
        if (!ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                .equals(state.getSlashArtsKey())) {
            return;
        }
        ResourceLocation entry = beginCast(
                player, blade, state, event.getType());
        if (!ComboStateRegistry.NONE.getId().equals(entry)) {
            event.setComboState(entry);
        }
    }

    static ResourceLocation beginCast(ServerPlayer player,
            ItemStack blade, ISlashBladeState state,
            SlashArts.ArtsType type) {
        PENDING.remove(player.getUUID());
        if (!ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                .equals(state.getSlashArtsKey())) {
            return ComboStateRegistry.NONE.getId();
        }
        ForgedSlashArtPlan plan = ForgedSlashArtPlan.from(blade);
        if (plan == null || type == null || type == SlashArts.ArtsType.Fail) {
            return ComboStateRegistry.NONE.getId();
        }
        plan = plan.snapshotForAttack(
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));

        ResourceLocation primaryEntry = ForgedNativeComboFlow.entry(
                plan.primary(), type, player);
        if (ComboStateRegistry.NONE.getId().equals(primaryEntry)) {
            return primaryEntry;
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
                type,
                created,
                created + plan.primaryDelayTicks()));
        return primaryEntry;
    }

    static void tick(TickEvent.ServerTickEvent event) {
        discardUnsafeNativeEntities(event);

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
            ForgedSlashArtPlan currentPlan = ForgedSlashArtPlan.from(blade);
            if (state == null || currentPlan == null
                    || !pending.plan.key().equals(currentPlan.key())
                    || !ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                            .equals(state.getSlashArtsKey())) {
                iterator.remove();
                continue;
            }

            ResourceLocation currentCombo = state.getComboSeq();
            ForgedSlashArtPlan.Technique activeTechnique =
                    pending.phase == Phase.PRIMARY
                            ? pending.plan.primary() : pending.plan.secondary();

            if (!pending.armed) {
                if (player.tickCount <= pending.createdPlayerTick) {
                    continue;
                }
                if (!ForgedNativeComboFlow.owns(
                        pending.plan.primary(), currentCombo)) {
                    iterator.remove();
                    continue;
                }
                pending.armed = true;
            }

            if (pending.phase == Phase.PRIMARY
                    && !pending.primaryExecuted
                    && player.tickCount >= pending.phaseDamageDueTick) {
                executePhase(player, state, pending,
                        pending.plan.primary(),
                        pending.plan.primaryCount(),
                        pending.plan.primaryDamagePerHit());
                pending.primaryExecuted = true;
            } else if (pending.phase == Phase.SECONDARY
                    && !pending.secondaryExecuted
                    && player.tickCount >= pending.phaseDamageDueTick) {
                executePhase(player, state, pending,
                        pending.plan.secondary(),
                        pending.plan.secondaryCount(),
                        pending.plan.secondaryDamagePerHit());
                pending.secondaryExecuted = true;
            }

            // SlashBlade owns every state transition inside the source graph.
            if (ForgedNativeComboFlow.owns(activeTechnique, currentCombo)) {
                continue;
            }

            // A foreign committed combo is player/add-on interruption, not a
            // natural source completion. Never inject B over it.
            if (!ForgedNativeComboFlow.isRecovery(currentCombo)) {
                iterator.remove();
                continue;
            }

            if (pending.phase == Phase.PRIMARY) {
                if (!pending.primaryExecuted) {
                    executePhase(player, state, pending,
                            pending.plan.primary(),
                            pending.plan.primaryCount(),
                            pending.plan.primaryDamagePerHit());
                    pending.primaryExecuted = true;
                }
                if (!startSecondary(player, state, pending)) {
                    iterator.remove();
                }
                continue;
            }

            if (!pending.secondaryExecuted) {
                executePhase(player, state, pending,
                        pending.plan.secondary(),
                        pending.plan.secondaryCount(),
                        pending.plan.secondaryDamagePerHit());
                pending.secondaryExecuted = true;
            }

            if (pending.secondaryCycle < pending.plan.secondaryCycles()) {
                if (!startSecondary(player, state, pending)) {
                    iterator.remove();
                }
                continue;
            }

            iterator.remove();
        }
    }

    /**
     * Native source callbacks are allowed to create their real entities, but
     * those entities become presentation-only while a forged native graph owns
     * the player. Forged budget entities are spawned under FORGED_OUTPUT_DEPTH
     * and therefore keep their bounded damage.
     */
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || FORGED_OUTPUT_DEPTH.get() > 0) {
            return;
        }
        Entity entity = event.getEntity();
        ServerPlayer owner = nativeOwner(entity);
        PendingCast pending = owner == null ? null : PENDING.get(owner.getUUID());
        if (pending == null || !ownsCurrentNativeGraph(owner, pending)) {
            return;
        }

        LegacyFusionCombatSupport.markVisualOnly(entity);
        if (entity instanceof EntityAbstractSummonedSword sword) {
            sword.setDamage(0.0D);
        }
        if (entity instanceof EntitySlashEffect slash) {
            slash.setDamage(0.0D);
        }
        if (entity instanceof EntityJudgementCut judgement) {
            judgement.setDamage(0.0D);
            // Native Judgement bursts into potion effects after its ten-tick
            // presentation. Keep the real slashdim lifecycle, but cut it off
            // before that combat-only cleanup path.
            PENDING_DISCARDS.add(new PendingDiscard(
                    level.dimension(), judgement.getUUID(),
                    level.getGameTime() + 10L));
        }
    }

    /** Suppresses direct native melee/projectile damage during the source graph. */
    static void onLivingAttack(LivingAttackEvent event) {
        if (SoulLegacyDamageGuard.isSecondary(event.getSource())) {
            return;
        }
        Entity causing = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        ServerPlayer owner = causing instanceof ServerPlayer player && direct == player
                ? player
                : nativeOwner(direct);
        PendingCast pending = owner == null ? null : PENDING.get(owner.getUUID());
        if (pending != null && ownsCurrentNativeGraph(owner, pending)) {
            event.setCanceled(true);
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING.values().removeIf(
                pending -> pending.dimension.equals(level.dimension()));
        PENDING_DISCARDS.removeIf(
                pending -> pending.dimension.equals(level.dimension()));
    }

    static void clear() {
        PENDING.clear();
        PENDING_DISCARDS.clear();
        FORGED_OUTPUT_DEPTH.remove();
    }

    private static boolean startSecondary(ServerPlayer player,
            ISlashBladeState state, PendingCast pending) {
        ResourceLocation secondaryEntry = ForgedNativeComboFlow.entry(
                pending.plan.secondary(), pending.sourceType, player);
        if (ComboStateRegistry.NONE.getId().equals(secondaryEntry)) {
            return false;
        }

        state.updateComboSeq(player, secondaryEntry);
        if (!ForgedNativeComboFlow.owns(
                pending.plan.secondary(), state.getComboSeq())) {
            return false;
        }

        pending.phase = Phase.SECONDARY;
        pending.secondaryCycle++;
        pending.secondaryExecuted = false;
        pending.phaseDamageDueTick =
                player.tickCount + pending.plan.secondaryDelayTicks();
        return true;
    }

    private static void executePhase(ServerPlayer player,
            ISlashBladeState state, PendingCast pending,
            ForgedSlashArtPlan.Technique technique,
            int count, double damagePerHit) {
        LivingEntity target = resolveTarget(player, pending.targetId);
        FORGED_OUTPUT_DEPTH.set(FORGED_OUTPUT_DEPTH.get() + 1);
        try {
            ProceduralSlashArtExecutor.executeForged(
                    player,
                    state,
                    pending.aim,
                    target,
                    technique,
                    pending.plan.modifier(),
                    count,
                    damagePerHit,
                    0,
                    pending.plan.angleScale());
        } finally {
            int remaining = FORGED_OUTPUT_DEPTH.get() - 1;
            if (remaining <= 0) {
                FORGED_OUTPUT_DEPTH.remove();
            } else {
                FORGED_OUTPUT_DEPTH.set(remaining);
            }
        }
    }

    private static void discardUnsafeNativeEntities(TickEvent.ServerTickEvent event) {
        Iterator<PendingDiscard> iterator = PENDING_DISCARDS.iterator();
        while (iterator.hasNext()) {
            PendingDiscard pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.discardAt) {
                continue;
            }
            iterator.remove();
            Entity entity = level.getEntity(pending.entityId);
            if (entity instanceof EntityJudgementCut) {
                entity.discard();
            }
        }
    }

    private static ServerPlayer nativeOwner(Entity entity) {
        if (!(entity instanceof EntityAbstractSummonedSword
                || entity instanceof EntitySlashEffect
                || entity instanceof EntityJudgementCut)) {
            return null;
        }
        if (entity instanceof IShootable shootable
                && shootable.getShooter() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static boolean ownsCurrentNativeGraph(
            ServerPlayer player, PendingCast pending) {
        ItemStack blade = player.getMainHandItem();
        ISlashBladeState state = blade.getCapability(ItemSlashBlade.BLADESTATE)
                .orElse(null);
        if (state == null) {
            return false;
        }
        ForgedSlashArtPlan.Technique activeTechnique =
                pending.phase == Phase.PRIMARY
                        ? pending.plan.primary() : pending.plan.secondary();
        return ForgedNativeComboFlow.owns(activeTechnique, state.getComboSeq());
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

    private enum Phase {
        PRIMARY,
        SECONDARY
    }

    private static final class PendingCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final UUID targetId;
        private final ForgedSlashArtPlan plan;
        private final Vec3 aim;
        private final SlashArts.ArtsType sourceType;
        private final int createdPlayerTick;
        private int phaseDamageDueTick;
        private Phase phase = Phase.PRIMARY;
        private boolean armed;
        private boolean primaryExecuted;
        private boolean secondaryExecuted;
        private int secondaryCycle;

        private PendingCast(ResourceKey<Level> dimension,
                UUID playerId,
                UUID targetId,
                ForgedSlashArtPlan plan,
                Vec3 aim,
                SlashArts.ArtsType sourceType,
                int createdPlayerTick,
                int phaseDamageDueTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.plan = plan;
            this.aim = aim;
            this.sourceType = sourceType;
            this.createdPlayerTick = createdPlayerTick;
            this.phaseDamageDueTick = phaseDamageDueTick;
        }
    }

    private record PendingDiscard(
            ResourceKey<Level> dimension,
            UUID entityId,
            long discardAt) {
    }

    private ForgedSlashArtHandler() {
    }
}
