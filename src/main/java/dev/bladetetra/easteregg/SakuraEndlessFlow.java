package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Senbonzakura's dispersed-only Rank burn and native B-combo continuation.
 * Native combo nodes, hits, damage and stun callbacks remain unchanged.
 * Each player's state is ephemeral; no new sword NBT or copied Rank currency.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SakuraEndlessFlow {
    private static final String DISPERSED = "blade_tetra_awakened_v2_sakura_dispersed";
    private static final ResourceLocation B1 = ComboStateRegistry.COMBO_B1.getId();
    private static final ResourceLocation B5 = ComboStateRegistry.COMBO_B5.getId();
    private static final ResourceLocation B6 = ComboStateRegistry.COMBO_B6.getId();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean eligible(ItemStack stack) {
        return AwakenedBladeCombat.replacesLegacy()
                && StyleResolver.resolve(stack) == BladeStyle.RENGEKI
                && SoulLegacyState.active(stack) == SoulLegacyState.Legacy.SENBONZAKURA
                && SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.SENBONZAKURA)
                && stack.getTag() != null && stack.getTag().getBoolean(DISPERSED);
    }

    public static boolean isBurning(ServerPlayer player, ItemStack blade) {
        Session session = SESSIONS.get(player.getUUID());
        return player.isAlive() && session != null && session.matches(player, blade)
                && session.flow.valid(player.level().getGameTime()) && eligible(blade)
                && player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                    .map(rank -> rank.getRankPoint(player.level().getGameTime()) > 0).orElse(false);
    }

    public static int initialRank(ServerPlayer player, ItemStack blade) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.matches(player, blade) ? session.flow.startRank : 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void input(InputCommandEvent event) {
        ServerPlayer player = event.getEntity();
        ItemStack blade = player.getMainHandItem();
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.matches(player, blade) || !eligible(blade)) return;
        // Only physical press edges create a token. Holding right click is still SA.
        boolean pressed = event.getCurrent().contains(InputCommand.L_CLICK) && !event.getOld().contains(InputCommand.L_CLICK)
                || event.getCurrent().contains(InputCommand.R_CLICK) && !event.getOld().contains(InputCommand.R_CLICK);
        if (!pressed) return;
        if (event.getCurrent().contains(InputCommand.SNEAK) && event.getCurrent().contains(InputCommand.R_CLICK)) {
            session.input.clear();
            return;
        }
        blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> {
            if (SakuraFlowRules.isNativeBAttack(state.getComboSeq()) && player.level().getGameTime() > state.getLastActionTime())
                session.input.queue(state.getComboSeq(), state.getLastActionTime(), player.level().getGameTime());
        });
    }

    private static boolean queued(ServerPlayer player, ItemStack blade) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.matches(player, blade)
                && blade.getCapability(ModularSlashBladeItem.BLADESTATE).map(state ->
                    session.input.pending(state.getComboSeq(), state.getLastActionTime(), player.level().getGameTime())).orElse(false);
    }

    /** Pure routing: virtual NextComboEvent queries never start sessions or debit Rank. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNextCombo(SlashBladeEvent.NextComboEvent event) {
        if (!eligible(event.getBlade())) return;
        ResourceLocation current = event.getSlashBladeState().getComboSeq();
        if (B5.equals(current) && B6.equals(event.getNextCombo()) && SakuraSixthCombo.compatible()) {
            event.setNextCombo(SakuraSixthCombo.SIXTH.getId());
        } else if (SakuraSixthCombo.SIXTH.getId().equals(current)) {
            // A completed B6 may yield to an actual native stance command even
            // after Rank burns out. Read the existing root; do not invent inputs.
            if (SakuraFlowRules.completedB6(ComboState.getElapsed(event.getUser()))) {
                var root = ComboStateRegistry.REGISTRY.get().getValue(event.getSlashBladeState().getComboRoot());
                var command = root == null ? null : root.getNext(event.getUser());
                if (SakuraStanceRules.action(command) != 0) {
                    event.setNextCombo(command);
                    return;
                }
            }
            // Preserve the upstream early-input self guard. A legitimate native
            // input call after all seven B6 hit ticks can restart B1.
            if (!SakuraFlowRules.completedB6(ComboState.getElapsed(event.getUser()))
                    || !(event.getUser() instanceof ServerPlayer player)
                    || !isBurning(player, event.getBlade())
                    || !(queued(player, event.getBlade()) || event.getUser().getCapability(CapabilityInputState.INPUT_STATE)
                        .map(input -> input.getCommands(event.getUser()).contains(InputCommand.R_CLICK)
                            || input.getCommands(event.getUser()).contains(InputCommand.L_CLICK)).orElse(false)))
                event.setNextCombo(current);
            else event.setNextCombo(B1);
        } else if (B6.equals(current)
                && ComboStateRegistry.COMBO_B7.getId().equals(event.getNextCombo())) {
            // A weapon switched to dispersed in the middle of a native B6
            // finishes that node naturally; it must never enter B7 to exit.
            event.setNextCombo(current);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
            return;
        ItemStack blade = player.getMainHandItem();
        UUID id = player.getUUID();
        if (!player.isAlive() || !eligible(blade)) {
            SESSIONS.remove(id);
            return;
        }
        Session session = SESSIONS.computeIfAbsent(id, ignored -> new Session(player, blade));
        if (!session.matches(player, blade)) {
            session = new Session(player, blade);
            Session replacement = session;
            blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(state -> replacement.flow.observe(
                    player.level().getGameTime(), SakuraFlowRules.stage(state.getComboSeq()), state.getLastActionTime(), 0, 0));
            SESSIONS.put(id, session);
        }
        Session activeSession = session;
        player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                .ifPresent(rank -> tick(player, blade, rank, activeSession));
    }

    private static void tick(ServerPlayer player, ItemStack blade,
            IConcentrationRank rank, Session session) {
        long now = player.serverLevel().getGameTime();
        long points = Math.max(0, rank.getRankPoint(now));

        // A monotonically nonincreasing ceiling prevents *any* native or
        // third-party positive Rank credit from funding dispersed attacks.
        // Actual Rank is always kept in SlashBlade's capability and HUD.
        if (session.ceiling < 0) session.ceiling = points;
        if (points > session.ceiling) {
            rank.addRankPoint(player, session.ceiling - points);
            points = Math.max(0, rank.getRankPoint(now));
        }
        session.ceiling = Math.min(session.ceiling, points);
        var state = blade.getCapability(ModularSlashBladeItem.BLADESTATE).orElse(null);
        if (state == null) return;
        ResourceLocation combo = state.resolvCurrentComboState(player);
        if (session.stopCombo != null) {
            if (state.getLastActionTime() == session.stopAt && session.stopCombo.equals(state.getComboSeq())) {
                session.flow.stop();
                session.input.clear();
            }
            session.stopCombo = null;
        }
        int stage = SakuraFlowRules.stage(combo);
        session.flow.observe(now, stage, state.getLastActionTime(), rank.getRank(now).level, points);
        int cost = session.flow.charge(now, points, GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get(),
                GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get());
        if (cost > 0) {
            rank.addRankPoint(player, -cost);
            session.ceiling = Math.min(session.ceiling, rank.getRankPoint(now));
        }
        // Completion runs at END, after the native tick has delivered its hits.
        // Never synthesize input commands or advance during item use/SA charging.
        if (!session.flow.valid(now) || player.isPassenger() || player.isSpectator()) {
            session.input.clear();
            return;
        }
        if (player.isUsingItem()) return;
        boolean pending = session.input.pending(state.getComboSeq(), state.getLastActionTime(), now);
        boolean held = player.getCapability(CapabilityInputState.INPUT_STATE)
                .map(input -> input.getCommands(player).contains(InputCommand.L_CLICK)
                    && !input.getCommands(player).contains(InputCommand.R_CLICK)).orElse(false);
        if (!pending) session.input.clear();
        if ((!pending && !held) || stage != 0 && now - state.getLastActionTime() < SakuraFlowRules.minimumAutoTick(stage)) return;
        if (stage == 0 && (!held || !combo.equals(ComboStateRegistry.NONE.getId()))) return;
        var root = ComboStateRegistry.REGISTRY.get().getValue(state.getComboRoot());
        ResourceLocation command = root == null ? null : root.getNext(player);
        if (command != null && !command.equals(B1) && !command.equals(ComboStateRegistry.NONE.getId())) {
            session.input.clear(); // directional and airborne moves remain under native input
            return;
        }
        ResourceLocation next = state.progressCombo(player, true);
        if (SakuraFlowRules.stage(next) == (stage == 0 || stage == 6 ? 1 : stage + 1)) {
            state.progressCombo(player); // native events/cancellation/clickAction still own the commit
            session.input.clear(); // exactly one attempt, including a canceled commit
            if (next.equals(state.getComboSeq()) && state.getLastActionTime() == now) {
                // inventoryTick has already run. Deliver the newly committed
                // node's tick 0 once now; next inventoryTick will deliver tick 1.
                // Otherwise END-tick continuation silently loses every B rush's
                // first native slash. Never replay an uncommitted/canceled node.
                var node = ComboStateRegistry.REGISTRY.get().getValue(next);
                if (node != null) node.tickAction(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void slashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (!(event.getEntityLiving() instanceof ServerPlayer player) || event.getComboState() == null
                || event.getType() == mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Fail) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.matches(player, player.getMainHandItem())) {
            session.stopCombo = event.getComboState();
            session.stopAt = player.level().getGameTime();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { SESSIONS.remove(event.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static final class Session {
        final ItemStack blade;
        final int slot;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        long ceiling = -1L;
        final SakuraFlowSession flow = new SakuraFlowSession();
        final SakuraFlowInput input = new SakuraFlowInput();
        ResourceLocation stopCombo;
        long stopAt;

        Session(ServerPlayer player, ItemStack blade) {
            this.blade = blade; this.dimension = player.level().dimension(); this.slot = player.getInventory().selected;
        }
        boolean matches(ServerPlayer player, ItemStack blade) {
            return this.blade == blade && slot == player.getInventory().selected && dimension.equals(player.level().dimension());
        }
    }

    private SakuraEndlessFlow() {}
}
