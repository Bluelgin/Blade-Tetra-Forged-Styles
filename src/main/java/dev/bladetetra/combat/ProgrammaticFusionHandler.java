package dev.bladetetra.combat;

import com.mojang.logging.LogUtils;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import dev.bladetetra.compat.fusion.RecastingFusionCompatibility;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static dev.bladetetra.combat.FusionSourceExecution.*;

/** Owns a server-side cast until SlashBlade completes its native state chain. */
public final class ProgrammaticFusionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_SOURCE_TICKS = 200;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ThreadLocal<Session> CHARGE = new ThreadLocal<>();
    private static final ThreadLocal<Timeline> TIMELINE = new ThreadLocal<>();

    public static boolean available(ResourceLocation art) {
        return registeredArt(art) != null && RecastingFusionCompatibility.canSelect(art);
    }

    public static ResourceLocation normalCombo(LivingEntity user) {
        return selectDuringCharge(user, SlashArts.ArtsType.Success);
    }

    public static ResourceLocation justCombo(LivingEntity user) {
        return selectDuringCharge(user, SlashArts.ArtsType.Jackpot);
    }

    private static ResourceLocation selectDuringCharge(LivingEntity user, SlashArts.ArtsType type) {
        Session cast = CHARGE.get();
        if (cast == null || cast.player != user || cast.selecting || cast.type != null
                || cast.phase != Phase.PREPARE) return none();
        selectFirst(cast, type);
        return cast.selected == null ? none() : cast.selected.combo();
    }

    private static void selectFirst(Session cast, SlashArts.ArtsType type) {
        cast.type = type;
        cast.selected = selectSource(cast, cast.plan.releaseAbility());
        if (cast.selected == null) {
            cast.second = true;
            cast.selected = selectSource(cast, cast.plan.responseAbility());
        }
    }

    private static Selection selectSource(Session cast, ResourceLocation id) {
        cast.selecting = true;
        try {
            return select(id, cast.type, cast.player, cast.state);
        } finally {
            cast.selecting = false;
        }
    }

    /** The original release computes the charge tier, runs its checks and posts its original event. */
    public static ResourceLocation charge(ISlashBladeState state, LivingEntity user, int elapsed,
            Supplier<ResourceLocation> nativeCharge) {
        if (!(user instanceof ServerPlayer player) || !hasFusion(state)) return nativeCharge.get();
        if (busy(player)) return none();
        Session cast = begin(player, state, elapsed);
        if (cast == null) return none();

        Session previous = CHARGE.get();
        CHARGE.set(cast);
        try {
            nativeCharge.get();
            if (cast.committed == null) {
                end(cast, "native release did not commit");
                return none();
            }
            enterTimeline(cast, cast.committed);
            return cast.committed;
        } catch (RuntimeException | LinkageError failure) {
            failed(cast, failure);
            return cast.committed == null ? none() : cast.committed;
        } finally {
            if (previous == null) CHARGE.remove();
            else CHARGE.set(previous);
        }
    }

    /** The native Super entry has already validated the player and applied its one durability charge. */
    public static ResourceLocation releaseSuper(SlashArts art, SlashArts.ArtsType type, LivingEntity user) {
        if (art != ModSlashBladeAbilities.PROGRAMMATIC_FUSION.get()) return art.doArts(type, user);
        if (!(user instanceof ServerPlayer player) || busy(player)) return none();
        ISlashBladeState state = player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .orElse(null);
        Session cast = state != null && hasFusion(state) ? begin(player, state, 0) : null;
        if (cast == null) return none();

        try {
            selectFirst(cast, SlashArts.ArtsType.Super);
            commit(cast);
        } catch (RuntimeException | LinkageError failure) {
            failed(cast, failure);
        }
        // We own the commit, including event delivery and verification.
        return none();
    }

    public static SlashArts contextualArt(ISlashBladeState state) {
        SlashArts scoped = sourceArt(state);
        if (scoped != null) return scoped;
        Session cast = CHARGE.get();
        return cast != null && cast.state == state && cast.selected != null
                && cast.phase == Phase.PREPARE ? cast.selected.art() : null;
    }

    static void observeRelease(SlashBladeEvent.PerformSlashArtEvent event) {
        Session cast = CHARGE.get();
        if (cast != null && cast.player == event.getEntityLiving()
                && cast.state == event.getSlashBladeState() && cast.phase == Phase.PREPARE) {
            if (cast.releaseEvent != null || cast.selected == null) event.setCanceled(true);
            else cast.releaseEvent = event;
        }
    }

    /** Scope the native timeout resolver; external motion alone cannot prove natural completion. */
    public static Map.Entry<Integer, ResourceLocation> resolveTimeout(ISlashBladeState state,
            LivingEntity user, Supplier<Map.Entry<Integer, ResourceLocation>> resolve) {
        Session cast = session(state, user);
        if (cast == null || !cast.executing() || cast.resolving || cast.inMotion) return resolve.get();

        cast.resolving = true;
        try {
            return withSource(state, cast.selected.art(), resolve);
        } catch (RuntimeException | LinkageError failure) {
            failed(cast, failure);
            return Map.entry(0, state.getComboSeq());
        } finally {
            cast.timeout = null;
            cast.resolving = false;
        }
    }

    static void observeTimeout(SlashBladeEvent.NextOfTimeOutComboEvent event) {
        Session cast = session(event.getSlashBladeState(), event.getUser());
        if (cast != null && cast.resolving && cast.executing() && !cast.inMotion
                && cast.timeout == null) {
            cast.timeout = new Timeout(event, event.getNextCombo());
        }
    }

    static void observeMotion(BladeMotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session cast = SESSIONS.get(player.getUUID());
        if (cast != null && cast.player == player && cast.inMotion && cast.motionEvent == null) {
            cast.motionEvent = event;
        }
    }

    /** Wrap the existing SlashBlade motion implementation, including its cancellable event and clickAction. */
    public static void motion(ISlashBladeState state, LivingEntity user, ResourceLocation requested,
            Runnable nativeMotion) {
        Session cast = session(state, user);
        if (cast == null) {
            nativeMotion.run();
            return;
        }
        if (cast.inMotion) {
            end(cast, "nested motion");
            return;
        }
        boolean entry = cast.committing || cast.phase == Phase.PREPARE
                && cast.selected != null && cast.releaseEvent != null
                && !cast.releaseEvent.isCanceled() && requested != null
                && requested.equals(cast.releaseEvent.getComboState());
        if (!cast.held()) {
            end(cast, "weapon or player changed before motion");
            if (!entry) nativeMotion.run();
            return;
        }
        if (entry) {
            Motion actual = performMotion(cast, nativeMotion);
            if (actual != null) cast.committed = actual.combo();
            return;
        }
        if (!cast.executing() && cast.phase != Phase.HANDOFF) {
            nativeMotion.run();
            return;
        }

        Timeout timeout = cast.timeout;
        cast.timeout = null;
        Motion before = Motion.read(state);
        boolean natural = before.equals(cast.motion) && cast.resolving && timeout != null
                && !timeout.event().isCanceled()
                && requested != null && requested.equals(timeout.original())
                && requested.equals(timeout.event().getNextCombo())
                && cast.frame >= NativeFusionLifecycle.requiredFrame(cast.selected.id(), before.combo());
        Motion after = performMotion(cast, nativeMotion);
        if (!tracked(cast)) return;
        if (natural && after != null && requested.equals(after.combo())) {
            if (neutral(after.combo())) {
                if (cast.second || !available(cast.plan.responseAbility())) end(cast, "complete");
                else {
                    cast.phase = Phase.HANDOFF;
                    cast.motion = after;
                }
            } else {
                cast.motion = after;
                cast.frame = -1;
            }
        } else if (timeout != null || !before.equals(Motion.read(state))) {
            end(cast, "motion interrupted or timeout did not commit");
        }
    }

    private static Motion performMotion(Session cast, Runnable nativeMotion) {
        Motion before = Motion.read(cast.state);
        cast.inMotion = true;
        try {
            nativeMotion.run();
            Motion after = Motion.read(cast.state);
            BladeMotionEvent event = cast.motionEvent;
            return event != null && !event.isCanceled() && !after.equals(before)
                    && after.committed(event.getCombo(), cast.now()) ? after : null;
        } finally {
            cast.motionEvent = null;
            cast.inMotion = false;
        }
    }

    private static void commit(Session cast) {
        Selection selected = cast.selected;
        if (selected == null || !cast.held() || !cast.motion.equals(Motion.read(cast.state))
                || cast.state.isBroken() || cast.state.isSealed()) {
            end(cast, "source unavailable");
            return;
        }
        cast.committing = true;
        try {
            withSource(cast.state, selected.art(), () -> {
                Motion before = Motion.read(cast.state);
                var event = new SlashBladeEvent.PerformSlashArtEvent(cast.player, cast.elapsed,
                        cast.state, selected.combo(), selected.type());
                if (!canEnter(before, selected.combo()) || MinecraftForge.EVENT_BUS.post(event)
                        || !tracked(cast) || !cast.held() || !before.equals(Motion.read(cast.state))
                        || !canEnter(before, event.getComboState())) {
                    end(cast, "source release cancelled or rejected");
                    return null;
                }
                cast.committed = null;
                cast.state.updateComboSeq(cast.player, event.getComboState());
                if (cast.committed != null) {
                    enterTimeline(cast, cast.committed);
                } else {
                    end(cast, "source motion did not commit");
                }
                return null;
            });
        } finally {
            cast.committing = false;
        }
    }

    private static boolean canEnter(Motion before, ResourceLocation next) {
        ComboState current = registeredCombo(before.combo());
        ComboState target = registeredCombo(next);
        return current != null && target != null && !neutral(next)
                && !next.equals(before.combo()) && current.getPriority() > target.getPriority();
    }

    private static void enterTimeline(Session cast, ResourceLocation combo) {
        if (!tracked(cast)) return;
        if (!cast.selected.combo().equals(combo)) {
            end(cast, "release event replaced the source combo");
            return;
        }
        cast.phase = cast.second ? Phase.EXECUTING_B : Phase.EXECUTING_A;
        cast.motion = Motion.read(cast.state);
        cast.frame = -1;
        cast.deadline = cast.now() + MAX_SOURCE_TICKS;
        // A release can occur after this player's inventory tick. Execute the real
        // frame zero here, and let the tick hook suppress another call in this tick.
        registeredCombo(combo).tickAction(cast.player);
    }

    public static void tickAction(ComboState combo, LivingEntity user, Runnable nativeTick) {
        if (!(user instanceof ServerPlayer player)) {
            nativeTick.run();
            return;
        }
        Session cast = SESSIONS.get(player.getUUID());
        if (cast == null || !cast.executing() || !cast.held()
                || !cast.motion.equals(Motion.read(cast.state))
                || registeredCombo(cast.motion.combo()) != combo) {
            nativeTick.run();
            return;
        }
        int frame = (int) cast.state.getElapsedTime(user);
        if (frame <= cast.frame) return;
        cast.frame = frame;
        Timeline previous = TIMELINE.get();
        TIMELINE.set(new Timeline(cast, frame));
        try {
            withSource(cast.state, cast.selected.art(), () -> {
                nativeTick.run();
                return null;
            });
        } catch (RuntimeException | LinkageError failure) {
            failed(cast, failure);
        } finally {
            if (previous == null) TIMELINE.remove();
            else TIMELINE.set(previous);
        }
    }

    public static int timelineFrame(LivingEntity user) {
        Timeline timeline = TIMELINE.get();
        return timeline != null && timeline.cast().player == user ? timeline.frame() : -1;
    }

    static void tick(TickEvent.ServerTickEvent event) {
        for (Session cast : List.copyOf(SESSIONS.values())) {
            if (cast.player.getServer() != event.getServer()) continue;
            try {
                if (!cast.held() || !cast.motion.equals(Motion.read(cast.state))) {
                    end(cast, "player, weapon, configuration or combo changed");
                } else if (cast.now() >= cast.deadline) {
                    end(cast, "native combo chain timed out");
                } else if (cast.phase == Phase.HANDOFF) {
                    cast.second = true;
                    cast.selected = selectSource(cast, cast.plan.responseAbility());
                    commit(cast);
                } else if (cast.frame < 0 && cast.motion.started() == cast.now()) {
                    registeredCombo(cast.motion.combo()).tickAction(cast.player);
                }
            } catch (RuntimeException | LinkageError failure) {
                failed(cast, failure);
            }
        }
    }

    static void removePlayer(ServerPlayer player, String reason) {
        Session cast = SESSIONS.get(player.getUUID());
        if (cast != null && cast.player == player) end(cast, reason);
    }

    static void onLevelUnload(ServerLevel level) {
        SESSIONS.values().removeIf(cast -> cast.level == level);
    }

    static void clear() {
        SESSIONS.clear();
    }

    private static Session begin(ServerPlayer player, ISlashBladeState state, int elapsed) {
        ItemStack blade = player.getMainHandItem();
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(blade);
        if (plan == null || blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null) != state) return null;
        Session previous = SESSIONS.get(player.getUUID());
        if (previous != null) end(previous, "new fusion release");
        Session cast = new Session(player, blade, state, plan, elapsed);
        SESSIONS.put(player.getUUID(), cast);
        return cast;
    }

    private static boolean busy(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        return existing != null && (existing.phase == Phase.PREPARE || existing.committing
                || existing.selecting || existing.inMotion || existing.resolving
                || existing.started == existing.now()
                || (TIMELINE.get() != null && TIMELINE.get().cast() == existing));
    }

    private static Session session(ISlashBladeState state, LivingEntity user) {
        if (!(user instanceof ServerPlayer)) return null;
        Session cast = SESSIONS.get(user.getUUID());
        return cast != null && cast.player == user && cast.state == state ? cast : null;
    }

    private static boolean hasFusion(ISlashBladeState state) {
        return ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId().equals(state.getSlashArtsKey());
    }

    private static boolean tracked(Session cast) {
        return SESSIONS.get(cast.player.getUUID()) == cast;
    }

    private static void end(Session cast, String reason) {
        SESSIONS.remove(cast.player.getUUID(), cast);
        LOGGER.debug("Fusion {} player={} phase={} source={} combo={} started={} reason={}",
                cast.plan.key(), cast.player.getUUID(), cast.phase,
                cast.selected == null ? null : cast.selected.id(), cast.state.getComboSeq(),
                cast.started, reason);
    }

    private static void failed(Session cast, Throwable failure) {
        end(cast, "native callback threw");
        LOGGER.warn("Native fusion {} failed in {} for {}", cast.plan.key(), cast.phase,
                cast.player.getUUID(), failure);
    }

    private static ResourceLocation none() {
        return ComboStateRegistry.NONE.getId();
    }

    private enum Phase { PREPARE, EXECUTING_A, HANDOFF, EXECUTING_B }

    /** Counts native state writes even when a combo is restarted within one game tick. */
    public interface MotionRevision {
        long bladeTetra$motionRevision();
    }

    private record Motion(ResourceLocation combo, long started, long revision) {
        static Motion read(ISlashBladeState state) {
            return new Motion(state.getComboSeq(), state.getLastActionTime(),
                    state instanceof MotionRevision motion ? motion.bladeTetra$motionRevision() : 0);
        }

        boolean committed(ResourceLocation expected, long now) {
            return expected != null && expected.equals(combo) && now == started;
        }
    }

    private record Timeout(SlashBladeEvent.NextOfTimeOutComboEvent event, ResourceLocation original) {}
    private record Timeline(Session cast, int frame) {}

    private static final class Session {
        final ServerPlayer player;
        final ServerLevel level;
        final ItemStack blade;
        final ISlashBladeState state;
        final ProgrammaticFusionPlan plan;
        final int elapsed;
        final long started;
        long deadline;
        Phase phase = Phase.PREPARE;
        SlashArts.ArtsType type;
        Selection selected;
        ResourceLocation committed;
        SlashBladeEvent.PerformSlashArtEvent releaseEvent;
        Timeout timeout;
        BladeMotionEvent motionEvent;
        Motion motion;
        int frame = -1;
        boolean second;
        boolean selecting;
        boolean committing;
        boolean resolving;
        boolean inMotion;

        Session(ServerPlayer player, ItemStack blade, ISlashBladeState state,
                ProgrammaticFusionPlan plan, int elapsed) {
            this.player = player;
            this.level = player.serverLevel();
            this.blade = blade;
            this.state = state;
            this.plan = plan;
            this.elapsed = elapsed;
            this.started = now();
            this.deadline = started + MAX_SOURCE_TICKS;
            this.motion = Motion.read(state);
        }

        boolean executing() {
            return phase == Phase.EXECUTING_A || phase == Phase.EXECUTING_B;
        }

        boolean held() {
            return player.isAlive() && !player.isRemoved() && player.level() == level
                    && player.getMainHandItem() == blade
                    && blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null) == state
                    && hasFusion(state) && plan.equals(ProgrammaticFusionPlan.from(blade));
        }

        long now() {
            return level.getGameTime();
        }
    }

    private ProgrammaticFusionHandler() {}
}
