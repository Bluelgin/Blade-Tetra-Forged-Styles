package dev.bladetetra.easteregg;

import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One atomic server-authoritative debit of SlashBlade's actual concentration
 * rank. No mod-local charge, rank NBT copy or synthetic per-player currency.
 * Rank naturally rises again from native attacks; addRankPoint syncs its HUD.
 *
 * Minimum rank is checked BEFORE payment; after a successful cast dropping
 * below that rank is intentional. Failed predicates must precede this call.
 */
public final class AwakenedRankCost {
    public enum Cast {
        KYOUKA, SAKURA_ARRAY, RAIKIRI_STEP, RAIKIRI_FINISH
    }

    public static boolean trySpend(ServerPlayer player, ItemStack blade, Cast cast) {
        if (!GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) return true;
        int cost = cost(blade, cast);
        int required = requiredLevel(blade, cast);
        return player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                .map(rank -> debit(player, rank, required, cost)).orElseGet(() -> {
                    warn(player, required, cost);
                    return false;
                });
    }

    private static boolean debit(ServerPlayer player, IConcentrationRank rank,
            int required, int cost) {
        long now = player.serverLevel().getGameTime();
        long points = Math.max(0, rank.getRankPoint(now));
        // Some add-ons override the unit; defaults below assume native 300.
        if (!canPay(points, rank.getUnitCapacity(), rank.getRank(now).level,
                required, cost)) {
            warn(player, required, cost);
            return false;
        }
        // AddRankPoint always owns rank decay, clamping and rank HUD sync.
        if (cost > 0) rank.addRankPoint(player, -cost);
        return true;
    }

    /**
     * Compensating payment for a cast whose post-debit extra hit was rejected
     * by another mod's protection or the target. Caller MUST invoke this only
     * after one successful trySpend and an actual failed server-side effect.
     * Native SA costs are independent and never reimbursed here.
     */
    public static void refund(ServerPlayer player, ItemStack blade, Cast cast) {
        if (!GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) return;
        int paid = cost(blade, cast);
        if (paid <= 0) return;
        player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                .ifPresent(rank -> rank.addRankPoint(player, paid));
    }

    /** Pure transaction precondition, shared with regression tests. */
    static boolean canPay(long points, long unitCapacity, int currentLevel,
            int minimumLevel, int price) {
        return unitCapacity > 0 && points >= 0 && price >= 0
                && currentLevel >= minimumLevel
                && points >= (long) minimumLevel * unitCapacity && points >= price;
    }

    public static boolean fused(ItemStack blade) {
        SoulResonance.Resonance resonance = SoulResonance.resolve(blade);
        return SoulResonance.isFormed(blade, resonance);
    }

    public static int cost(ItemStack blade, Cast cast) {
        return cost(cast) + (fused(blade)
                ? GameplayConfig.AWAKENED_RANK_COST_DUAL_BONUS.get() : 0);
    }

    public static int requiredLevel(ItemStack blade, Cast cast) {
        return fused(blade) ? 5 : requiredLevel(cast);
    }

    public static int cost(Cast cast) {
        return switch (cast) {
            case KYOUKA -> GameplayConfig.AWAKENED_RANK_COST_KYOUKA.get();
            case SAKURA_ARRAY -> GameplayConfig.AWAKENED_RANK_COST_SAKURA.get();
            case RAIKIRI_STEP -> GameplayConfig.AWAKENED_RANK_COST_STEP.get();
            case RAIKIRI_FINISH -> GameplayConfig.AWAKENED_RANK_COST_FINISH.get();
        };
    }

    public static int requiredLevel(Cast cast) {
        return switch (cast) {
            case KYOUKA, SAKURA_ARRAY, RAIKIRI_FINISH -> 4; // A
            case RAIKIRI_STEP -> 3; // B
        };
    }

    private static void warn(ServerPlayer player, int required, int cost) {
        player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.awakened.rank_insufficient",
                        rankName(required), cost)
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static String rankName(int level) {
        return switch (level) {
            case 5 -> "S";
            case 4 -> "A";
            case 3 -> "B";
            default -> "D";
        };
    }

    private AwakenedRankCost() {}
}
