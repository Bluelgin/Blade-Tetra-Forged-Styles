package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Transient sprint cadence only: no chase windows, kill transfers or saved resources. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class RengekiRuntimeState {
    private static final Map<UUID, MovementSample> MOVEMENT_SAMPLES = new HashMap<>();
    private static final Map<UUID, SprintChainState> SPRINT_CHAINS = new HashMap<>();

    static Map<UUID, MovementSample> movementSamples() { return MOVEMENT_SAMPLES; }
    static Map<UUID, SprintChainState> sprintChains() { return SPRINT_CHAINS; }

    static void clearMomentum(UUID playerId) {
        MOVEMENT_SAMPLES.remove(playerId);
        SPRINT_CHAINS.remove(playerId);
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearMomentum(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearMomentum(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void onClone(PlayerEvent.Clone event) {
        clearMomentum(event.getOriginal().getUUID());
        clearMomentum(event.getEntity().getUUID());
    }

    record MovementSample(Vec3 position, long gameTime) {}
    static final class SprintChainState {
        long nextVisualBeatAt = Long.MIN_VALUE;
        long nextHitAt = Long.MIN_VALUE;
        int nextBeat;
        int activeBeat = -1;
        int burstTick;
        float visualSize;
    }

    private RengekiRuntimeState() {}
}
