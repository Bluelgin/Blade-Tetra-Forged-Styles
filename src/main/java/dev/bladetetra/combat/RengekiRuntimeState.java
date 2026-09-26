package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared transient state owner for Rengeki sub-features.
 *
 * <p>Momentum and short-step mechanics remain behaviorally independent, but
 * player lifecycle cleanup is centralized so logout/dimension/clone cannot leave
 * half of the style runtime alive.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class RengekiRuntimeState {
    private static final Map<UUID, MovementSample> MOVEMENT_SAMPLES = new HashMap<>();
    private static final Map<UUID, SprintChainState> SPRINT_CHAINS = new HashMap<>();
    private static final Map<UUID, ChaseWindow> CHASE_WINDOWS = new HashMap<>();
    private static final Map<UUID, KillTransfer> KILL_TRANSFERS = new HashMap<>();

    static Map<UUID, MovementSample> movementSamples() {
        return MOVEMENT_SAMPLES;
    }

    static Map<UUID, SprintChainState> sprintChains() {
        return SPRINT_CHAINS;
    }

    static Map<UUID, ChaseWindow> chaseWindows() {
        return CHASE_WINDOWS;
    }

    static Map<UUID, KillTransfer> killTransfers() {
        return KILL_TRANSFERS;
    }

    static void clearMomentum(UUID playerId) {
        MOVEMENT_SAMPLES.remove(playerId);
        SPRINT_CHAINS.remove(playerId);
    }

    static void clearShortStep(UUID playerId) {
        CHASE_WINDOWS.remove(playerId);
        KILL_TRANSFERS.remove(playerId);
    }

    static void clear(UUID playerId) {
        clearMomentum(playerId);
        clearShortStep(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        clear(event.getOriginal().getUUID());
        clear(event.getEntity().getUUID());
    }

    record MovementSample(Vec3 position, long gameTime) {
    }

    static final class SprintChainState {
        long nextVisualBeatAt = Long.MIN_VALUE;
        long nextHitAt = Long.MIN_VALUE;
        int nextBeat;
        int activeBeat = -1;
        int burstTick;
        float visualSize;
    }

    record ChaseWindow(long expiresAt, ItemStack blade) {
    }

    record KillTransfer(long readyAt, long expiresAt, ItemStack blade) {
    }

    private RengekiRuntimeState() {
    }
}
