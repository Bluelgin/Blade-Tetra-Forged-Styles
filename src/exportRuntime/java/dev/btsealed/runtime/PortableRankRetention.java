package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Standalone counterpart of Blade Tetra's precision-habaki native Rank decay clock.
 *
 * <p>Keep the player's native SlashBlade Rank capability as the sole owner of Rank.
 * Every second consecutive protected tick advances its native last-update clock
 * by one tick. Explicit gains, expenditures, floors and sync are not duplicated.
 * State keys are weak to avoid retaining disconnected/dimension-replaced players.</p>
 */
final class PortableRankRetention {
    private static final Map<ServerPlayer, Clock> CLOCKS = new WeakHashMap<>();

    static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;
        var profile = PortableProfiles.forBlade(player.getMainHandItem(), LogicalSide.SERVER);
        boolean protectedBlade = player.isAlive() && !player.isSpectator()
                && profile != null && profile.effects().precisionHabaki();

        long now = player.level().getGameTime();
        Clock clock = CLOCKS.computeIfAbsent(player, ignored -> new Clock());
        if (clock.advance(now, protectedBlade)) {
            player.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank -> {
                if (rank.getLastUpdate() < now) {
                    rank.setLastUpdte(rank.getLastUpdate() + 1L);
                }
            });
        }
    }

    /** Mirrors the source precision-habaki consecutive-tick counter. */
    static final class Clock {
        private long previousTick = Long.MIN_VALUE;
        private boolean previousProtected;
        private int consecutiveProtected;

        boolean advance(long now, boolean protectedBlade) {
            boolean compensate = false;
            if (now == previousTick + 1L && previousProtected) {
                if (++consecutiveProtected == 2) {
                    compensate = true;
                    consecutiveProtected = 0;
                }
            } else {
                consecutiveProtected = 0;
            }
            previousTick = now;
            previousProtected = protectedBlade;
            return compensate;
        }
    }

    private PortableRankRetention() {}
}
