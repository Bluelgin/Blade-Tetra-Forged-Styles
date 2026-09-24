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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One source state machine at a time, observed after the normal inventory tick. */
final class ProgrammaticFusionPresentationRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SOFT_OVERLAP_MIN_TICKS = 3;
    private static final int SOFT_OVERLAP_MAX_TICKS = 6;
    private static final int SOFT_OVERLAP_DEADLINE_TICKS = 20;
    private static final Map<UUID, PendingResponse> PENDING_RESPONSES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED_FAILURES = ConcurrentHashMap.newKeySet();

    static boolean delegateRelease(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ProgrammaticFusionPlan plan) {
        // Even a fallback/new cast must invalidate the old response.
        cancel(player.getUUID(), "new fusion cast");
        ProgrammaticFusionPresentation presentation = plan.releasePresentation();
        if (!presentation.delegateRelease()) return false;
        ResourceLocation ability = plan.releaseAbility();
        try {
            SlashArts art = registeredArt(ability);
            if (art == null) return failed(ability, "release SA missing");
            ResourceLocation combo = art.doArts(event.getType(), player);
            FusionHandoff.Route route = auditedRoute(presentation, combo);
            boolean overlap = route == null
                    && presentation.softOverlap()
                    && validSoftOverlapSelection(combo);
            if (route == null && !overlap) {
                return failed(ability,
                        "release combo missing or unsupported by audit/soft-overlap: " + combo);
            }
            event.setComboState(combo);
            LOGGER.debug("Programmatic fusion release source={} combo={} policy={} fallback=false",
                    ability, combo, route != null ? route.policy() : "SOFT_OVERLAP");
            return true;
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("Programmatic fusion release failed for {}", ability, failure);
            return failed(ability, "release runtime/linkage failure");
        }
    }

    static boolean scheduleResponse(SlashBladeEvent.PerformSlashArtEvent event,
            ServerPlayer player, ProgrammaticFusionPlan plan, boolean delegatedRelease) {
        ResourceLocation releaseCombo = event.getComboState();
        try {
            long now = player.level().getGameTime();
            FusionHandoff.Route route = delegatedRelease
                    ? auditedRoute(plan.releasePresentation(), releaseCombo)
                    : NativeFusionPresentationAudit.semanticRoute(String.valueOf(releaseCombo));
            FusionHandoff.Tracker tracker = null;
            FusionHandoff.SoftOverlapTracker overlapTracker = null;

            if (delegatedRelease && route == null
                    && plan.releasePresentation().softOverlap()) {
                ComboState combo = registeredCombo(releaseCombo);
                if (combo == null || !validSoftOverlapSelection(releaseCombo)) {
                    return failed(plan.releaseAbility(),
                            "soft-overlap release combo unavailable; response uses semantic fallback");
                }
                int timeoutTicks = timeoutTicks(combo);
                int overlapTicks = softOverlapTicks(timeoutTicks);
                overlapTracker = new FusionHandoff.SoftOverlapTracker(
                        releaseCombo.toString(), timeoutTicks, now, overlapTicks,
                        SOFT_OVERLAP_DEADLINE_TICKS);
            } else {
                // DIRECT semantic release has already spawned an independent drive.
                if (!delegatedRelease && ComboStateRegistry.STANDBY.getId().equals(releaseCombo)) {
                    ComboState cs = ComboStateRegistry.STANDBY.get();
                    route = new FusionHandoff.Route(List.of(new FusionHandoff.Stage(
                            releaseCombo.toString(), cs.getStartFrame(), cs.getEndFrame(),
                            cs.getSpeed(), cs.timeout, 1)), 10);
                }
                if (route == null || !validRoute(route)) {
                    return failed(plan.releaseAbility(),
                            "no audited handoff; response uses semantic fallback");
                }
                List<Integer> timeouts = route.stages().stream().map(stage -> {
                    ComboState cs = registeredCombo(new ResourceLocation(stage.combo()));
                    return timeoutTicks(cs);
                }).toList();
                tracker = new FusionHandoff.Tracker(route, timeouts, now);
            }

            SlashArts.ArtsType type = event.getType() == SlashArts.ArtsType.Jackpot
                    ? SlashArts.ArtsType.Jackpot : SlashArts.ArtsType.Success;
            PendingResponse pending = new PendingResponse(player.level().dimension(), plan.key(),
                    plan.releaseAbility(), plan.responseAbility(), player.getMainHandItem(),
                    event.getSlashBladeState(), event, releaseCombo, type,
                    route, tracker, overlapTracker, now);
            PENDING_RESPONSES.put(player.getUUID(), pending);
            LOGGER.debug("Programmatic fusion release source={} combo={} policy={} handoff due={} response source={} fallback={}",
                    plan.releaseAbility(), releaseCombo, policy(pending), due(pending),
                    plan.responseAbility(), !plan.responsePresentation().delegateResponse());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("Programmatic fusion scheduling failed", failure);
            return failed(plan.responseAbility(), "scheduling runtime/linkage failure");
        }
    }

    static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Source callbacks may post events and schedule a new fusion: never hold a map iterator.
        for (UUID id : new ArrayList<>(PENDING_RESPONSES.keySet())) {
            PendingResponse pending = PENDING_RESPONSES.get(id);
            if (pending == null) continue;
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (level == null || player == null || player.level() != level || !player.isAlive()) {
                cancel(id, "player unavailable/dead/dimension changed");
                continue;
            }
            ItemStack blade = player.getMainHandItem();
            ISlashBladeState state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
            ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(blade);
            if (blade != pending.blade() || state != pending.state() || plan == null
                    || !pending.planKey().equals(plan.key())
                    || !ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId().equals(state.getSlashArtsKey())) {
                cancel(id, "weapon/state/plan changed");
                continue;
            }
            if (pending.event().isCanceled()
                    || !pending.initialCombo().equals(pending.event().getComboState())) {
                cancel(id, "release event cancelled/replaced");
                continue;
            }
            try {
                long now = level.getGameTime();
                // An event can be posted late in a tick; allow the commit until the next END.
                if (now == pending.created()
                        && state.getLastActionTime() != pending.created()) {
                    continue;
                }

                String previous = observedCombo(pending);
                FusionHandoff.Decision decision;
                if (pending.overlapTracker() != null) {
                    ResourceLocation comboId = state.getComboSeq();
                    ComboState combo = registeredCombo(comboId);
                    if (combo == null) {
                        failed(pending.releaseAbility(),
                                "soft-overlap source entered missing combo " + comboId);
                        decision = FusionHandoff.Decision.EXPIRED;
                    } else {
                        decision = pending.overlapTracker().observe(
                                comboId.toString(), state.getLastActionTime(), now,
                                timeoutTicks(combo));
                    }
                } else {
                    decision = pending.tracker().observe(
                            state.getComboSeq().toString(), state.getLastActionTime(), now);
                }

                String current = observedCombo(pending);
                if (!previous.equals(current)) {
                    LOGGER.debug("Programmatic fusion source progression {} -> {} handoff due={}",
                            previous, current, due(pending));
                }
                if (decision == FusionHandoff.Decision.WAIT) continue;

                PENDING_RESPONSES.remove(id, pending);
                LOGGER.debug("Programmatic fusion release source={} combo={} policy={} handoff reason={} response source={}",
                        pending.releaseAbility(), state.getComboSeq(), policy(pending),
                        decision, pending.responseAbility());

                // External input/recast owns the state; never inject B over it.
                if (decision == FusionHandoff.Decision.INTERRUPTED) continue;

                if (decision == FusionHandoff.Decision.EXPIRED
                        || !triggerResponse(player, state, plan, pending)) {
                    LOGGER.debug("Programmatic fusion response source={} fallback=true",
                            pending.responseAbility());
                    ProgrammaticFusionHandler.executeSemanticResponse(player, plan);
                }
            } catch (RuntimeException | LinkageError failure) {
                PENDING_RESPONSES.remove(id, pending);
                failed(pending.responseAbility(), "pending runtime/linkage failure");
                LOGGER.debug("Programmatic fusion pending failed", failure);
                ProgrammaticFusionHandler.executeSemanticResponse(player, plan);
            }
        }
    }

    private static boolean triggerResponse(ServerPlayer player, ISlashBladeState state,
            ProgrammaticFusionPlan plan, PendingResponse pending) {
        ResourceLocation ability = pending.responseAbility();
        ProgrammaticFusionPresentation presentation = plan.responsePresentation();
        if (!presentation.delegateResponse()) return false;
        try {
            SlashArts art = registeredArt(ability);
            if (art == null) return failed(ability, "response SA missing");
            return FusionSourceExecution.enter(() -> art.doArts(pending.type(), player),
                    combo -> auditedRoute(presentation, combo) != null
                            || presentation.softOverlap()
                                    && validSoftOverlapSelection(combo),
                    combo -> state.updateComboSeq(player, combo),
                    combo -> {
                        boolean committed = combo.equals(state.getComboSeq())
                                && state.getLastActionTime() == player.level().getGameTime();
                        LOGGER.debug("Programmatic fusion response source={} combo={} committed={} policy={} fallback={}",
                                ability, combo, committed,
                                auditedRoute(presentation, combo) != null
                                        ? "AUDITED" : "SOFT_OVERLAP",
                                !committed);
                        return committed;
                    });
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.debug("Programmatic fusion response failed for {}", ability, failure);
            return failed(ability, "response runtime/linkage failure");
        }
    }

    static FusionHandoff.Route auditedRoute(ProgrammaticFusionPresentation presentation,
            ResourceLocation combo) {
        if (combo == null) return null;
        FusionHandoff.Route route = presentation.route(combo.toString());
        return route != null && validRoute(route) ? route : null;
    }

    private static boolean validRoute(FusionHandoff.Route route) {
        for (FusionHandoff.Stage stage : route.stages()) {
            ResourceLocation id = new ResourceLocation(stage.combo());
            ComboState cs = registeredCombo(id);
            if (cs == null || cs.getLoop() || cs.getStartFrame() != stage.start()
                    || cs.getEndFrame() != stage.end()
                    || Float.compare(cs.getSpeed(), stage.speed()) != 0
                    || cs.timeout != stage.extraTimeoutMs()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validSoftOverlapSelection(ResourceLocation combo) {
        return combo != null && !isNeutral(combo) && registeredCombo(combo) != null;
    }

    private static boolean isNeutral(ResourceLocation combo) {
        return ComboStateRegistry.NONE.getId().equals(combo)
                || ComboStateRegistry.STANDBY.getId().equals(combo);
    }

    private static ComboState registeredCombo(ResourceLocation combo) {
        return combo != null && ComboStateRegistry.REGISTRY.get().containsKey(combo)
                ? ComboStateRegistry.REGISTRY.get().getValue(combo) : null;
    }

    private static int timeoutTicks(ComboState combo) {
        if (combo == null) return 1;
        // SlashBlade transitions on elapsed milliseconds > timeout, not >=.
        return Math.max(1, combo.getTimeoutMS() / 50 + 1);
    }

    static int softOverlapTicks(int timeoutTicks) {
        return Math.max(SOFT_OVERLAP_MIN_TICKS,
                Math.min(SOFT_OVERLAP_MAX_TICKS, timeoutTicks / 4));
    }

    private static String observedCombo(PendingResponse pending) {
        return pending.overlapTracker() != null
                ? pending.overlapTracker().combo() : pending.tracker().combo();
    }

    private static long due(PendingResponse pending) {
        return pending.overlapTracker() != null
                ? pending.overlapTracker().due() : pending.tracker().due();
    }

    private static String policy(PendingResponse pending) {
        return pending.overlapTracker() != null
                ? "SOFT_OVERLAP" : String.valueOf(pending.route().policy());
    }

    private static SlashArts registeredArt(ResourceLocation ability) {
        return ability != null && SlashArtsRegistry.REGISTRY.get().containsKey(ability)
                ? SlashArtsRegistry.REGISTRY.get().getValue(ability) : null;
    }

    private static boolean failed(ResourceLocation ability, String reason) {
        if (ability != null && WARNED_FAILURES.add(ability)) {
            LOGGER.warn("Programmatic fusion presentation for {} fell back: {}", ability, reason);
        }
        LOGGER.debug("Programmatic fusion source={} reason={} fallback=true", ability, reason);
        return false;
    }

    private static void cancel(UUID id, String reason) {
        PendingResponse pending = PENDING_RESPONSES.remove(id);
        if (pending != null) {
            LOGGER.debug("Programmatic fusion response source={} cancelled: {}",
                    pending.responseAbility(), reason);
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING_RESPONSES.entrySet().removeIf(
                entry -> entry.getValue().dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING_RESPONSES.clear();
        WARNED_FAILURES.clear();
    }

    private record PendingResponse(ResourceKey<Level> dimension, String planKey,
            ResourceLocation releaseAbility, ResourceLocation responseAbility,
            ItemStack blade, ISlashBladeState state,
            SlashBladeEvent.PerformSlashArtEvent event,
            ResourceLocation initialCombo, SlashArts.ArtsType type,
            FusionHandoff.Route route, FusionHandoff.Tracker tracker,
            FusionHandoff.SoftOverlapTracker overlapTracker, long created) { }

    private ProgrammaticFusionPresentationRuntime() {
    }
}
