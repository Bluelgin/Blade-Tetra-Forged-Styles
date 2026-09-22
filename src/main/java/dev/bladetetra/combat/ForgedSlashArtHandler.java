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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Executes the player-authored Tetra Slash Art without invoking source SA logic. */
final class ForgedSlashArtHandler {
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
        int created = player.tickCount;
        PENDING.put(player.getUUID(), new PendingCast(
                player.level().dimension(), player.getUUID(), plan, aim,
                event.getComboState(), created,
                created + plan.primaryDelayTicks(),
                created + plan.secondaryDelayTicks()));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<Map.Entry<UUID, PendingCast>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCast> entry = iterator.next();
            PendingCast pending = entry.getValue();
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);
            if (level == null || player == null || player.level() != level || !player.isAlive()) {
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
                    // Another listener cancelled or redirected the visual motion.
                    iterator.remove();
                    continue;
                }
                pending.armed = true;
            } else if (!isCompatibleMotion(state.getComboSeq(), pending.expectedCombo)) {
                // Returning to NONE/STANDBY is normal recovery; another committed
                // attack means this authored cast was interrupted.
                iterator.remove();
                continue;
            }

            if (!pending.primaryExecuted && player.tickCount >= pending.primaryDueTick) {
                pending.primaryExecuted = true;
                ProceduralSlashArtExecutor.execute(player, pending.aim,
                        pending.plan.primary().response(), pending.plan.primaryCount(),
                        pending.plan.primaryDamagePerHit(), 0, pending.plan.angleScale());
            }

            if (player.tickCount < pending.secondaryDueTick) {
                continue;
            }
            for (int cycle = 0; cycle < pending.plan.secondaryCycles(); cycle++) {
                ProceduralSlashArtExecutor.execute(player, pending.aim,
                        pending.plan.secondary().response(), pending.plan.secondaryCount(),
                        pending.plan.secondaryDamagePerHit(),
                        cycle * pending.plan.echoSpacingTicks(), pending.plan.angleScale());
            }
            iterator.remove();
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING.values().removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    static void clear() {
        PENDING.clear();
    }

    private static boolean isCompatibleMotion(ResourceLocation current,
            ResourceLocation expected) {
        return expected == null
                || expected.equals(current)
                || ComboStateRegistry.NONE.getId().equals(current)
                || ComboStateRegistry.STANDBY.getId().equals(current);
    }

    private static final class PendingCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final ForgedSlashArtPlan plan;
        private final Vec3 aim;
        private final ResourceLocation expectedCombo;
        private final int createdPlayerTick;
        private final int primaryDueTick;
        private final int secondaryDueTick;
        private boolean armed;
        private boolean primaryExecuted;

        private PendingCast(ResourceKey<Level> dimension, UUID playerId,
                ForgedSlashArtPlan plan, Vec3 aim, ResourceLocation expectedCombo,
                int createdPlayerTick, int primaryDueTick, int secondaryDueTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.plan = plan;
            this.aim = aim;
            this.expectedCombo = expectedCombo;
            this.createdPlayerTick = createdPlayerTick;
            this.primaryDueTick = primaryDueTick;
            this.secondaryDueTick = secondaryDueTick;
        }
    }

    private ForgedSlashArtHandler() {
    }
}
