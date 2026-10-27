package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
    private static final int CHARGE_INTERVAL = 10;

    public static boolean eligible(ItemStack stack) {
        return AwakenedBladeCombat.replacesLegacy()
                && StyleResolver.resolve(stack) == BladeStyle.RENGEKI
                && SoulLegacyState.active(stack) == SoulLegacyState.Legacy.SENBONZAKURA
                && SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.SENBONZAKURA)
                && stack.getTag() != null && stack.getTag().getBoolean(DISPERSED);
    }

    public static boolean isBurning(ServerPlayer player, ItemStack blade) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.blade == blade
                && session.active && eligible(blade)
                && player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                    .map(rank -> rank.getRankPoint(player.level().getGameTime()) > 0).orElse(false);
    }

    public static int initialRank(ServerPlayer player, ItemStack blade) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.blade == blade ? session.startRank : 0;
    }

    /** Pure routing: virtual NextComboEvent queries never start sessions or debit Rank. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNextCombo(SlashBladeEvent.NextComboEvent event) {
        if (!eligible(event.getBlade())) return;
        ResourceLocation current = event.getSlashBladeState().getComboSeq();
        if (B5.equals(current) && B6.equals(event.getNextCombo())) {
            event.setNextCombo(SakuraSixthCombo.SIXTH.getId());
        } else if (SakuraSixthCombo.SIXTH.getId().equals(current)) {
            // Preserve the upstream early-input self guard. A legitimate native
            // input call after all seven B6 hit ticks can restart B1.
            if (!SakuraFlowRules.completedB6(ComboState.getElapsed(event.getUser()))
                    || !(event.getUser() instanceof ServerPlayer player)
                    || !isBurning(player, event.getBlade())
                    || !event.getUser().getCapability(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE)
                        .map(input -> input.getCommands(event.getUser()).contains(mods.flammpfeil.slashblade.util.InputCommand.R_CLICK)
                            || input.getCommands(event.getUser()).contains(mods.flammpfeil.slashblade.util.InputCommand.L_CLICK))
                        .orElse(false))
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
        Session session = SESSIONS.computeIfAbsent(id, ignored -> new Session(blade, player.level().dimension()));
        if (session.blade != blade || !session.dimension.equals(player.level().dimension())) {
            session = new Session(blade, player.level().dimension());
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
        if (points == 0) session.active = false;

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.resolvCurrentComboState(player))
                .orElse(ComboStateRegistry.NONE.getId());
        if (!SakuraFlowRules.isNativeBAttack(combo)) {
            session.active = false;
            session.seenAttack = false;
            return;
        }

        if (!session.seenAttack) {
            session.seenAttack = true;
            int grade = rank.getRank(now).level;
            if (grade >= 4) {
                session.active = true;
                session.startRank = Math.min(7, grade);
                session.nextCharge = now + CHARGE_INTERVAL;
            }
        }
        if (!session.active || !GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) return;
        if (now < session.nextCharge) return;

        int cost = SakuraFlowRules.halfSecondCost(session.startRank,
                GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get());
        if (points < cost || cost <= 0) {
            session.active = false;
            return;
        }
        rank.addRankPoint(player, -cost);
        session.ceiling = Math.min(session.ceiling, rank.getRankPoint(now));
        session.nextCharge = now + CHARGE_INTERVAL;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static final class Session {
        final ItemStack blade;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        long ceiling = -1L;
        long nextCharge;
        int startRank;
        boolean seenAttack;
        boolean active;

        Session(ItemStack blade, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
            this.blade = blade; this.dimension = dimension;
        }
    }

    private SakuraEndlessFlow() {}
}
