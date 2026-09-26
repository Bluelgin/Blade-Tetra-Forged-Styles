package dev.bladetetra.combat;

import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Routes a player-authored Slash Art through native SlashBlade ComboStates.
 *
 * <p>Blade Tetra only owns composition and handoff. SlashBlade owns the actual
 * animation, movement, effects, hit rules and damage. The primary source graph
 * is allowed to reach its defining attack callback, then this runtime splices
 * directly into the secondary technique's signature state. That makes the result
 * one continuous authored sequence rather than two complete Slash Arts played
 * back-to-back.</p>
 */
final class ForgedSlashArtHandler {
    private static final Map<UUID, PendingCast> PENDING = new HashMap<>();

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

        ResourceLocation primaryEntry = ForgedNativeComboFlow.entry(
                plan.primary(), type, player);
        if (ComboStateRegistry.NONE.getId().equals(primaryEntry)) {
            return primaryEntry;
        }

        PENDING.put(player.getUUID(), new PendingCast(
                player.level().dimension(),
                player.getUUID(),
                blade,
                plan.key(),
                plan.primary(),
                plan.secondary(),
                plan.modifier(),
                type,
                player.tickCount));
        return primaryEntry;
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<Map.Entry<UUID, PendingCast>> iterator =
                PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingCast pending = iterator.next().getValue();
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
            if (blade != pending.sourceBlade
                    || state == null || currentPlan == null
                    || !pending.planKey.equals(currentPlan.key())
                    || !ModSlashBladeAbilities.FORGED_SLASH_ART.getId()
                            .equals(state.getSlashArtsKey())) {
                iterator.remove();
                continue;
            }

            ResourceLocation currentCombo = state.getComboSeq();
            ForgedSlashArtPlan.Technique active = pending.phase == Phase.PRIMARY
                    ? pending.primary : pending.secondary;

            if (!pending.armed) {
                // The SlashArt event chooses the entry before SlashBlade commits
                // it. Wait one server tick, then require the native graph to have
                // actually started before we are allowed to splice anything.
                if (player.tickCount <= pending.createdPlayerTick) {
                    continue;
                }
                if (!ForgedNativeComboFlow.owns(
                        pending.primary, currentCombo)) {
                    iterator.remove();
                    continue;
                }
                pending.armed = true;
            }

            if (!ForgedNativeComboFlow.owns(active, currentCombo)) {
                // Any foreign committed state is a real interruption. We do not
                // force our authored route through player input or another mod.
                iterator.remove();
                continue;
            }

            if (!ForgedNativeComboFlow.shouldSplice(
                    active, currentCombo, player, pending.modifier)) {
                continue;
            }

            if (pending.phase == Phase.PRIMARY) {
                ResourceLocation secondaryEntry =
                        ForgedNativeComboFlow.secondaryEntry(
                                pending.secondary,
                                pending.sourceType,
                                player);
                if (!enterNativeState(
                        player, state, pending.secondary, secondaryEntry)) {
                    iterator.remove();
                    continue;
                }

                if (pending.modifier.repeatsSecondary()) {
                    pending.phase = Phase.SECONDARY_ECHO;
                    continue;
                }

                // The secondary signature and everything after it are now fully
                // SlashBlade-owned. No forged runtime needs to stay attached.
                iterator.remove();
                continue;
            }

            // Echo is the only modifier that needs a second handoff. Re-entering
            // the same native signature state resets its native timeline and
            // clickAction even when primary/secondary resolve to identical IDs.
            ResourceLocation echoEntry =
                    ForgedNativeComboFlow.secondaryEntry(
                            pending.secondary,
                            pending.sourceType,
                            player);
            enterNativeState(player, state, pending.secondary, echoEntry);
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

    private static boolean enterNativeState(
            ServerPlayer player,
            ISlashBladeState state,
            ForgedSlashArtPlan.Technique technique,
            ResourceLocation entry) {
        if (entry == null || ComboStateRegistry.NONE.getId().equals(entry)) {
            return false;
        }
        state.updateComboSeq(player, entry);
        return ForgedNativeComboFlow.owns(
                technique, state.getComboSeq());
    }

    private enum Phase {
        PRIMARY,
        SECONDARY_ECHO
    }

    private static final class PendingCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final ItemStack sourceBlade;
        private final String planKey;
        private final ForgedSlashArtPlan.Technique primary;
        private final ForgedSlashArtPlan.Technique secondary;
        private final ForgedSlashArtPlan.Modifier modifier;
        private final SlashArts.ArtsType sourceType;
        private final int createdPlayerTick;
        private Phase phase = Phase.PRIMARY;
        private boolean armed;

        private PendingCast(ResourceKey<Level> dimension,
                UUID playerId,
                ItemStack sourceBlade,
                String planKey,
                ForgedSlashArtPlan.Technique primary,
                ForgedSlashArtPlan.Technique secondary,
                ForgedSlashArtPlan.Modifier modifier,
                SlashArts.ArtsType sourceType,
                int createdPlayerTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.sourceBlade = sourceBlade;
            this.planKey = planKey;
            this.primary = primary;
            this.secondary = secondary;
            this.modifier = modifier;
            this.sourceType = sourceType;
            this.createdPlayerTick = createdPlayerTick;
        }
    }

    private ForgedSlashArtHandler() {
    }
}
