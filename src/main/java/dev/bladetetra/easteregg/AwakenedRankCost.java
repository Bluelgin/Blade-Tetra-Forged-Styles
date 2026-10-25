package dev.bladetetra.easteregg;

import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
        AKATSUKI, KYOUKA, SAKURA_ARRAY, RAIKIRI_STEP, RAIKIRI_FINISH
    }

    public static boolean trySpend(ServerPlayer player, Cast cast) {
        if (!GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) return true;
        return player.getCapability(CapabilityConcentrationRank.RANK_POINT)
                .map(rank -> debit(player, rank, cast)).orElseGet(() -> {
                    warn(player, cast);
                    return false;
                });
    }

    private static boolean debit(ServerPlayer player, IConcentrationRank rank,
            Cast cast) {
        int cost = cost(cast);
        int required = requiredLevel(cast);
        long now = player.serverLevel().getGameTime();
        long points = Math.max(0, rank.getRankPoint(now));
        // Some add-ons override the unit; defaults below assume native 300.
        long minimumPoints = (long) required * rank.getUnitCapacity();
        if (rank.getRank(now).level < required
                || points < minimumPoints || points < cost) {
            warn(player, cast);
            return false;
        }
        // AddRankPoint always owns rank decay, clamping and rank HUD sync.
        if (cost > 0) rank.addRankPoint(player, -cost);
        return true;
    }

    public static int cost(Cast cast) {
        return switch (cast) {
            case AKATSUKI -> GameplayConfig.AWAKENED_RANK_COST_AKATSUKI.get();
            case KYOUKA -> GameplayConfig.AWAKENED_RANK_COST_KYOUKA.get();
            case SAKURA_ARRAY -> GameplayConfig.AWAKENED_RANK_COST_SAKURA.get();
            case RAIKIRI_STEP -> GameplayConfig.AWAKENED_RANK_COST_STEP.get();
            case RAIKIRI_FINISH -> GameplayConfig.AWAKENED_RANK_COST_FINISH.get();
        };
    }

    public static int requiredLevel(Cast cast) {
        return switch (cast) {
            case AKATSUKI -> 5; // S
            case KYOUKA, SAKURA_ARRAY, RAIKIRI_FINISH -> 4; // A
            case RAIKIRI_STEP -> 3; // B
        };
    }

    private static void warn(ServerPlayer player, Cast cast) {
        player.displayClientMessage(Component.translatable(
                        "message.blade_tetra.awakened.rank_insufficient",
                        rankName(requiredLevel(cast)), cost(cast))
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
