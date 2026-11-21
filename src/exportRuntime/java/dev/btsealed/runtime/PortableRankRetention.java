package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Standalone counterpart of Blade Tetra's precision-habaki native Rank decay clock.
 *
 * <p>Keep the player's native SlashBlade Rank capability as the sole owner of Rank.
 * Every second consecutive protected tick advances its native last-update clock
 * by one tick. Explicit gains, expenditures, floors and sync are not duplicated.
 * State keys are weak to avoid retaining disconnected/dimension-replaced players.</p>
 */
public final class PortableRankRetention {
    private static final Map<ServerPlayer, Tracking> CLOCKS = new WeakHashMap<>();

    static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;
        var profile = PortableProfiles.forBlade(player.getMainHandItem(), LogicalSide.SERVER);
        boolean protectedBlade = player.isAlive() && !player.isSpectator()
                && profile != null && profile.effects().precisionHabaki();

        long now = player.level().getGameTime();
        // A different exported sword must not inherit the previous sword's half-tick.
        String activeBlade = protectedBlade
                ? player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                        .map(state -> state.getTranslationKey()).orElse(null)
                : null;
        Tracking tracker = CLOCKS.computeIfAbsent(player, ignored -> new Tracking());
        if (tracker.advance(now, activeBlade)) {
            player.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank -> {
                if (rank.getLastUpdate() < now) {
                    rank.setLastUpdte(rank.getLastUpdate() + 1L);
                }
            });
        }
    }

    /** Each exported weapon identity has to begin its own consecutive-tick window. */
    public static final class Tracking {
        private String activeBlade;
        private Clock clock = new Clock();

        public boolean advance(long now, String bladeId) {
            if (!Objects.equals(activeBlade, bladeId)) {
                // Match the source Mixin's tick ordering: the previous weapon's
                // final elapsed tick is settled BEFORE the held weapon changes.
                // Reset only after that settlement, so a different exported sword
                // never inherits the preceding blade's partial half-tick.
                boolean finalCredit = activeBlade != null && clock.advance(now, false);
                activeBlade = bladeId;
                clock = new Clock();
                if (bladeId != null) clock.advance(now, true);
                return finalCredit;
            }
            return clock.advance(now, bladeId != null);
        }
    }

    /** Mirrors the source precision-habaki consecutive-tick counter. */
    public static final class Clock {
        private long previousTick = Long.MIN_VALUE;
        private boolean previousProtected;
        private int consecutiveProtected;

        public boolean advance(long now, boolean protectedBlade) {
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
