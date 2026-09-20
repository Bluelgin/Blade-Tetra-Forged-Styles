package dev.bladetetra.combat;

import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegates exact, allow-listed add-on presentations without linking any optional
 * add-on classes. The saya/release side may replace the generic combo with the
 * source Slash Art's real ComboState. The tsuba/response side is blended in once
 * the release has had time to show its signature action, then starts the source
 * response ComboState directly.
 *
 * <p>Only IDs registered in {@link ProgrammaticFusionPresentations} can reach
 * this executor. Unknown add-ons stay on the semantic/Safe path.</p>
 */
final class ProgrammaticFusionPresentationRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_BLEND_DELAY_TICKS = 3;
    private static final int MAX_BLEND_DELAY_TICKS = 16;
    private static final double BLEND_TIMEOUT_FRACTION = 0.35D;

    private static final Map<UUID, PendingResponse> PENDING_RESPONSES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED_FAILURES =
            ConcurrentHashMap.newKeySet();

    static boolean delegateRelease(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ProgrammaticFusionPlan plan) {
        if (!plan.releasePresentation().delegateRelease()) {
            return false;
        }
        ResourceLocation ability = plan.releaseAbility();
        SlashArts art = registeredArt(ability);
        if (art == null) {
            warnOnce(ability, "registered source Slash Art is missing");
            return false;
        }

        try {
            ResourceLocation combo = art.doArts(event.getType(), player);
            if (!registeredCombo(combo)) {
                warnOnce(ability, "source Slash Art returned an unavailable ComboState " + combo);
                return false;
            }
            event.setComboState(combo);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(ability, "source release delegation failed: " + failure.getClass().getSimpleName());
            LOGGER.debug("Programmatic fusion release delegation failed for {}", ability, failure);
            return false;
        }
    }

    static boolean scheduleResponse(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ProgrammaticFusionPlan plan) {
        if (!plan.responsePresentation().delegateResponse()) {
            return false;
        }
        ResourceLocation ability = plan.responseAbility();
        if (registeredArt(ability) == null) {
            warnOnce(ability, "registered response Slash Art is missing");
            return false;
        }

        ResourceLocation releaseCombo = event.getComboState();
        int delay = responseDelayTicks(releaseCombo);
        SlashArts.ArtsType responseType = event.getType() == SlashArts.ArtsType.Jackpot
                ? SlashArts.ArtsType.Jackpot : SlashArts.ArtsType.Success;
        PENDING_RESPONSES.put(player.getUUID(), new PendingResponse(
                player.level().dimension(), player.getUUID(), plan.key(), ability,
                releaseCombo, player.tickCount, player.tickCount + delay,
                responseType, false));
        return true;
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<Map.Entry<UUID, PendingResponse>> iterator =
                PENDING_RESPONSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingResponse> entry = iterator.next();
            PendingResponse pending = entry.getValue();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            if (level == null || player == null || player.level() != level || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            ItemStack blade = player.getMainHandItem();
            ISlashBladeState state = blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .orElse(null);
            ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(blade);
            if (state == null || plan == null || !pending.planKey().equals(plan.key())
                    || !ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()
                            .equals(state.getSlashArtsKey())) {
                iterator.remove();
                continue;
            }

            if (!pending.armed()) {
                if (player.tickCount <= pending.createdPlayerTick()) {
                    continue;
                }
                if (pending.releaseCombo() != null
                        && !pending.releaseCombo().equals(state.getComboSeq())) {
                    // The original PerformSlashArtEvent was cancelled or replaced
                    // before SlashBlade committed our release ComboState.
                    iterator.remove();
                    continue;
                }
                pending = pending.armedCopy();
                entry.setValue(pending);
            }

            if (player.tickCount < pending.duePlayerTick()) {
                continue;
            }
            iterator.remove();

            if (!triggerResponse(player, state, pending)) {
                ProgrammaticFusionHandler.executeSemanticResponse(player, plan);
            }
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING_RESPONSES.values().removeIf(
                pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING_RESPONSES.clear();
        WARNED_FAILURES.clear();
    }

    static int responseDelayTicks(ResourceLocation releaseCombo) {
        if (!registeredCombo(releaseCombo)) {
            return MIN_BLEND_DELAY_TICKS;
        }
        ComboState combo = ComboStateRegistry.REGISTRY.get().getValue(releaseCombo);
        return combo == null ? MIN_BLEND_DELAY_TICKS
                : responseDelayTicksForTimeout(combo.getTimeoutMS());
    }

    static int responseDelayTicksForTimeout(int timeoutMs) {
        int fullTicks = Math.max(1,
                (int) Math.ceil(TimeValueHelper.getTicksFromMSec(Math.max(0, timeoutMs))));
        int blended = (int) Math.ceil(fullTicks * BLEND_TIMEOUT_FRACTION);
        return Mth.clamp(blended, MIN_BLEND_DELAY_TICKS, MAX_BLEND_DELAY_TICKS);
    }

    private static boolean triggerResponse(ServerPlayer player,
            ISlashBladeState state, PendingResponse pending) {
        ResourceLocation ability = pending.responseAbility();
        SlashArts art = registeredArt(ability);
        if (art == null) {
            warnOnce(ability, "response Slash Art disappeared before execution");
            return false;
        }
        try {
            ResourceLocation combo = art.doArts(pending.responseType(), player);
            if (!registeredCombo(combo)) {
                warnOnce(ability, "response Slash Art returned an unavailable ComboState " + combo);
                return false;
            }
            // updateComboSeq performs the source ComboState click action and leaves
            // subsequent tick actions to SlashBlade's normal state machine.
            state.updateComboSeq(player, combo);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(ability, "source response delegation failed: "
                    + failure.getClass().getSimpleName());
            LOGGER.debug("Programmatic fusion response delegation failed for {}",
                    ability, failure);
            return false;
        }
    }

    private static SlashArts registeredArt(ResourceLocation ability) {
        if (ability == null || !SlashArtsRegistry.REGISTRY.get().containsKey(ability)) {
            return null;
        }
        return SlashArtsRegistry.REGISTRY.get().getValue(ability);
    }

    private static boolean registeredCombo(ResourceLocation combo) {
        return combo != null && !ComboStateRegistry.NONE.getId().equals(combo)
                && ComboStateRegistry.REGISTRY.get().containsKey(combo);
    }

    private static void warnOnce(ResourceLocation ability, String reason) {
        if (ability != null && WARNED_FAILURES.add(ability)) {
            LOGGER.warn("Programmatic fusion presentation for {} fell back: {}",
                    ability, reason);
        }
    }

    private record PendingResponse(ResourceKey<Level> dimension,
            UUID playerId, String planKey, ResourceLocation responseAbility,
            ResourceLocation releaseCombo, int createdPlayerTick,
            int duePlayerTick, SlashArts.ArtsType responseType, boolean armed) {
        PendingResponse armedCopy() {
            return new PendingResponse(dimension, playerId, planKey, responseAbility,
                    releaseCombo, createdPlayerTick, duePlayerTick, responseType, true);
        }
    }

    private ProgrammaticFusionPresentationRuntime() {
    }
}
